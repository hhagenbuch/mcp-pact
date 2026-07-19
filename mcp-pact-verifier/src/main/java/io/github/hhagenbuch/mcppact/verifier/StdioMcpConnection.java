package io.github.hhagenbuch.mcppact.verifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.mcppact.core.Capabilities;
import io.github.hhagenbuch.mcppact.core.model.ServerSnapshot;
import io.github.hhagenbuch.mcppact.core.model.ServerTool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal MCP client over a server's stdio transport: newline-delimited
 * JSON-RPC 2.0. Self-contained (no SDK) so the verifier's tests are hermetic —
 * they launch a real subprocess but pull no dependencies at run time.
 *
 * <p>Not thread-safe; the verifier drives it sequentially. stderr is drained on
 * a daemon thread so a chatty server can't deadlock on a full pipe buffer.
 */
public final class StdioMcpConnection implements McpConnection {

    private final Process process;
    private final BufferedWriter stdin;
    private final BufferedReader stdout;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger ids = new AtomicInteger();
    private final Set<String> capabilities = new LinkedHashSet<>();

    public StdioMcpConnection(List<String> command) {
        try {
            this.process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to launch MCP server: " + command, e);
        }
        this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        drainAsync(process.getErrorStream());
        handshake();
    }

    private void handshake() {
        ObjectNode init = mapper.createObjectNode();
        init.put("protocolVersion", "2024-11-05");
        init.putObject("capabilities");
        ObjectNode clientInfo = init.putObject("clientInfo");
        clientInfo.put("name", "mcp-pact-verifier");
        clientInfo.put("version", "0.1.0");
        JsonNode result = request("initialize", init);
        capabilities.addAll(Capabilities.flatten(result.path("capabilities")));
        notification("notifications/initialized");
    }

    @Override
    public ServerSnapshot listTools() {
        JsonNode tools = request("tools/list", null).path("tools");
        List<ServerTool> serverTools = new ArrayList<>();
        for (JsonNode tool : tools) {
            serverTools.add(new ServerTool(
                    tool.path("name").asText(),
                    tool.hasNonNull("description") ? tool.path("description").asText() : null,
                    tool.path("inputSchema")));
        }
        return ServerSnapshot.of(serverTools, Set.copyOf(capabilities));
    }

    @Override
    public JsonNode call(String tool, JsonNode arguments) {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", tool);
        params.set("arguments", arguments == null ? mapper.createObjectNode() : arguments);
        return request("tools/call", params);
    }

    private JsonNode request(String method, JsonNode params) {
        try {
            int id = ids.incrementAndGet();
            ObjectNode req = mapper.createObjectNode();
            req.put("jsonrpc", "2.0");
            req.put("id", id);
            req.put("method", method);
            if (params != null) {
                req.set("params", params);
            }
            send(req);
            String expectedId = String.valueOf(id);
            String line;
            while ((line = stdout.readLine()) != null) {
                JsonNode node = mapper.readTree(line);
                // Match on the id's textual form: a server may echo our numeric id as a
                // JSON string, and asInt() on a non-numeric string collapses to 0.
                if (node.has("id") && node.path("id").asText().equals(expectedId)) {
                    if (node.has("error")) {
                        throw new IllegalStateException("MCP error for " + method + ": " + node.get("error"));
                    }
                    return node.path("result");
                }
            }
            throw new IllegalStateException("MCP server closed the stream before answering " + method);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void notification(String method) {
        try {
            ObjectNode req = mapper.createObjectNode();
            req.put("jsonrpc", "2.0");
            req.put("method", method);
            send(req);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void send(JsonNode node) throws IOException {
        stdin.write(mapper.writeValueAsString(node));
        stdin.write("\n");
        stdin.flush();
    }

    private void drainAsync(InputStream stream) {
        Thread drain = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    // discard server logs; keeping the pipe empty prevents a deadlock
                }
            } catch (IOException ignored) {
                // stream closed on shutdown
            }
        }, "mcp-stderr-drain");
        drain.setDaemon(true);
        drain.start();
    }

    @Override
    public void close() {
        try {
            stdin.close();
        } catch (IOException ignored) {
            // best-effort
        }
        process.destroy();
    }
}
