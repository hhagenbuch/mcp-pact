package io.github.hhagenbuch.mcppact.recorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A transparent stdio proxy: {@code client ↔ recorder ↔ server}. It launches the
 * real server, forwards every line each way, and tees each parsed JSON-RPC
 * message into a {@link RecorderSession}. Passthrough is line-faithful — each
 * newline-delimited message is forwarded intact (line reading normalizes CRLF,
 * which is immaterial to JSON-RPC). The server's stderr is forwarded to the
 * proxy's stderr so the client still sees server logs. Observation is
 * best-effort: a line that isn't JSON is forwarded but not recorded.
 */
public final class RecorderProxy {

    private final List<String> serverCommand;
    private final InputStream clientIn;
    private final OutputStream clientOut;
    private final RecorderSession session;
    private final ObjectMapper mapper = new ObjectMapper();

    public RecorderProxy(List<String> serverCommand, InputStream clientIn, OutputStream clientOut,
                         RecorderSession session) {
        this.serverCommand = serverCommand;
        this.clientIn = clientIn;
        this.clientOut = clientOut;
        this.session = session;
    }

    /** Runs until the client closes its input or the server exits. */
    public void run() {
        Process server;
        try {
            server = new ProcessBuilder(serverCommand).start();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to launch MCP server: " + serverCommand, e);
        }
        forwardStderrAsync(server.getErrorStream());

        // client → server: forward each request, observe it; EOF closes the server's stdin.
        Thread toServer = new Thread(() -> pump(clientIn, server.getOutputStream(),
                session::observeClientMessage), "recorder-client-to-server");
        // server → client: forward each response, observe it.
        Thread toClient = new Thread(() -> pump(server.getInputStream(), clientOut,
                session::observeServerMessage), "recorder-server-to-client");

        toServer.start();
        toClient.start();
        try {
            // In CLI use the client drives shutdown by closing stdin (EOF → toServer
            // ends → server stdin closed → server exits → toClient ends). A server that
            // crashes while the client holds stdin open would leave toServer blocked on
            // read; acceptable for the CLI, where the client owns the lifecycle.
            toServer.join();
            toClient.join();
            server.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.destroy();
        }
    }

    private void pump(InputStream in, OutputStream out, java.util.function.Consumer<JsonNode> observe) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.write("\n");
                writer.flush();
                observe(line, observe);
            }
        } catch (IOException e) {
            // a stream closing mid-session is normal shutdown, not an error
        }
    }

    private void observe(String line, java.util.function.Consumer<JsonNode> observe) {
        if (line.isBlank()) {
            return;
        }
        try {
            observe.accept(mapper.readTree(line));
        } catch (IOException notJson) {
            // forward-only; a non-JSON line is not recorded
        }
    }

    /** Forwards the server's stderr to ours so server logs stay visible through the proxy. */
    private void forwardStderrAsync(InputStream stream) {
        Thread forward = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println(line);
                }
            } catch (IOException ignored) {
                // stream closed on shutdown
            }
        }, "recorder-stderr-forward");
        forward.setDaemon(true);
        forward.start();
    }
}
