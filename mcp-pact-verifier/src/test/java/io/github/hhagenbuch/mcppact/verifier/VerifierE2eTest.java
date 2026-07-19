package io.github.hhagenbuch.mcppact.verifier;

import io.github.hhagenbuch.mcppact.core.Finding;
import io.github.hhagenbuch.mcppact.core.PactIO;
import io.github.hhagenbuch.mcppact.core.Report;
import io.github.hhagenbuch.mcppact.core.model.Pact;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives a real stdio provider subprocess ({@code ExampleMcpServer.java}) in
 * each drift mode and asserts the verifier catches exactly the right findings —
 * the tools/list diff and interaction replay working end to end over the wire.
 */
class VerifierE2eTest {

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static Path resource(String name) {
        try {
            return Path.of(VerifierE2eTest.class.getClassLoader().getResource(name).toURI());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Pact pact() {
        return PactIO.load(resource("search.mcp-pact.json"));
    }

    private static List<String> serverCommand(String mode) {
        return List.of(javaBin(), resource("server/ExampleMcpServer.java").toString(), mode);
    }

    private static Set<String> ruleKeys(List<Finding> findings) {
        return findings.stream().map(f -> f.severity() + ":" + f.rule()).collect(Collectors.toSet());
    }

    static Stream<Arguments> modes() {
        return Stream.of(
                Arguments.of("v1", Set.of()),
                Arguments.of("rename", Set.of("BREAKING:tool.missing", "COMPAT:tool.new")),
                Arguments.of("retype", Set.of("BREAKING:param.type")),
                Arguments.of("require", Set.of("BREAKING:param.newRequired")),
                Arguments.of("desc", Set.of("WARN:tool.description")),
                Arguments.of("breakresp", Set.of("BREAKING:response.matcher")));
    }

    @ParameterizedTest(name = "mode={0}")
    @MethodSource("modes")
    void catchesDriftOverStdio(String mode, Set<String> expected) {
        Report report;
        try (StdioMcpConnection connection = new StdioMcpConnection(serverCommand(mode))) {
            report = Verifier.verify(pact(), connection);
        }
        assertThat(ruleKeys(report.findings())).isEqualTo(expected);
    }
}
