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
 * real server, forwards every line each way unchanged, and tees each parsed
 * JSON-RPC message into a {@link RecorderSession}. Passthrough is byte-faithful
 * (the client and server behave exactly as if directly connected); observation
 * is best-effort (a line that isn't JSON is forwarded but not recorded).
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
        drainAsync(server.getErrorStream());

        // client → server: forward each request, observe it; EOF closes the server's stdin.
        Thread toServer = new Thread(() -> pump(clientIn, server.getOutputStream(),
                session::observeClientMessage), "recorder-client-to-server");
        // server → client: forward each response, observe it.
        Thread toClient = new Thread(() -> pump(server.getInputStream(), clientOut,
                session::observeServerMessage), "recorder-server-to-client");

        toServer.start();
        toClient.start();
        try {
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

    private void drainAsync(InputStream stream) {
        Thread drain = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    // discard server logs to keep the pipe from filling
                }
            } catch (IOException ignored) {
                // stream closed on shutdown
            }
        }, "recorder-stderr-drain");
        drain.setDaemon(true);
        drain.start();
    }
}
