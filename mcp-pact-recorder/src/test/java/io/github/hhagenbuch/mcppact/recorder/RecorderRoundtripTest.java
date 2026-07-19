package io.github.hhagenbuch.mcppact.recorder;

import io.github.hhagenbuch.mcppact.core.PactIO;
import io.github.hhagenbuch.mcppact.core.Report;
import io.github.hhagenbuch.mcppact.core.model.Pact;
import io.github.hhagenbuch.mcppact.verifier.StdioMcpConnection;
import io.github.hhagenbuch.mcppact.verifier.Verifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole pipeline: record real traffic through the proxy into a pact, then
 * verify that recorded pact — it must hold against the same server and catch a
 * breaking change on a mutated one.
 */
class RecorderRoundtripTest {

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static Path server() {
        try {
            return Path.of(RecorderRoundtripTest.class.getClassLoader()
                    .getResource("server/ExampleMcpServer.java").toURI());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> serverCommand(String mode) {
        return List.of(javaBin(), server().toString(), mode);
    }

    /** A scripted MCP client: handshake, list tools, call search_code once, then disconnect. */
    private static final String CLIENT_SCRIPT = String.join("\n",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"search_code\",\"arguments\":{\"query\":\"CartService\",\"limit\":5}}}") + "\n";

    private Pact record(@TempDir Path dir) {
        RecorderSession session = new RecorderSession();
        new RecorderProxy(serverCommand("v1"),
                new ByteArrayInputStream(CLIENT_SCRIPT.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(),
                session).run();
        Pact pact = session.toPact("support-agent", "example-tools");
        // Persist and reload so we also prove the on-disk pact is valid and usable.
        Path file = dir.resolve("recorded.mcp-pact.json");
        PactIO.write(file, pact);
        return PactIO.load(file);
    }

    private Report verifyAgainst(Pact pact, String mode) {
        try (StdioMcpConnection connection = new StdioMcpConnection(serverCommand(mode))) {
            return Verifier.verify(pact, connection);
        }
    }

    @Test
    void recordedPactHoldsAgainstTheSameServer(@TempDir Path dir) {
        Report report = verifyAgainst(record(dir), "v1");
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void recordedPactCatchesABreakingChange(@TempDir Path dir) {
        Report report = verifyAgainst(record(dir), "rename");
        assertThat(report.hasBreaking()).isTrue();
        assertThat(report.findings()).anyMatch(f -> f.rule().equals("tool.missing"));
    }
}
