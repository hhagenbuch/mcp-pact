package io.github.hhagenbuch.mcppact.verifier;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CLI contract: exit 0 when the contract holds, 1 on a breaking difference
 * (or a warning under {@code --strict}), 2 on a usage error.
 */
class CliTest {

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static Path resource(String name) {
        try {
            return Path.of(CliTest.class.getClassLoader().getResource(name).toURI());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String[] verify(String mode, String... flags) {
        String pact = resource("search.mcp-pact.json").toString();
        String server = resource("server/ExampleMcpServer.java").toString();
        // verify <pact> [flags...] -- <java> <server> <mode>
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add("verify");
        args.add(pact);
        java.util.Collections.addAll(args, flags);
        args.add("--");
        args.add(javaBin());
        args.add(server);
        args.add(mode);
        return args.toArray(new String[0]);
    }

    @Test
    void cleanContractExitsZero() {
        assertThat(Cli.run(verify("v1"))).isZero();
    }

    @Test
    void breakingContractExitsOne() {
        assertThat(Cli.run(verify("retype"))).isEqualTo(1);
    }

    @Test
    void warningPassesUnlessStrict() {
        assertThat(Cli.run(verify("desc"))).isZero();
        assertThat(Cli.run(verify("desc", "--strict"))).isEqualTo(1);
    }

    @Test
    void usageErrorsExitTwo() {
        assertThat(Cli.run(new String[]{})).isEqualTo(2);
        assertThat(Cli.run(new String[]{"verify", "some.json"})).isEqualTo(2); // no `-- server`
        assertThat(Cli.run(new String[]{"bogus"})).isEqualTo(2);
    }

    @Test
    void invalidPactExitsTwoNotOne() {
        // A malformed matcher regex is an invalid pact (exit 2), NOT a broken contract (exit 1).
        String badPact = resource("bad-regex.mcp-pact.json").toString();
        String[] args = {"verify", badPact, "--", javaBin(),
                resource("server/ExampleMcpServer.java").toString(), "v1"};
        assertThat(Cli.run(args)).isEqualTo(2);
    }

    @Test
    void missingPactFileExitsTwo() {
        String[] args = {"verify", "does-not-exist.mcp-pact.json", "--", javaBin(),
                resource("server/ExampleMcpServer.java").toString(), "v1"};
        assertThat(Cli.run(args)).isEqualTo(2);
    }
}
