package io.github.hhagenbuch.mcppact.core.diff;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.hhagenbuch.mcppact.core.Finding;
import io.github.hhagenbuch.mcppact.core.PactIO;
import io.github.hhagenbuch.mcppact.core.model.Expectation;
import io.github.hhagenbuch.mcppact.core.model.Pact;
import io.github.hhagenbuch.mcppact.core.model.ServerSnapshot;
import io.github.hhagenbuch.mcppact.core.model.ServerTool;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The breaking-change taxonomy, expressed as a table. Each row is a
 * pact-vs-provider scenario and the exact set of {@code SEVERITY:rule} findings
 * it must produce. This suite <em>is</em> the spec for {@link ContractVerifier}.
 */
class ContractVerifierTest {

    private static JsonNode json(String s) {
        try {
            return PactIO.mapper().readTree(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A consumer that sends {query:string, limit:number}, requiring `query`.
    private static final String CONSUMER_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},"
                    + "\"limit\":{\"type\":\"number\"}},\"required\":[\"query\"]}";

    private static Expectation expectation(String tool, String description, String schema, List<String> caps) {
        return new Expectation(tool, description, json(schema), caps, List.of());
    }

    private static Pact pact(Expectation expectation) {
        return new Pact("0.1", "support-agent", "workspace-tools", List.of(expectation));
    }

    private static ServerSnapshot snapshot(List<ServerTool> tools, Set<String> caps) {
        return ServerSnapshot.of(tools, caps);
    }

    private static Set<String> ruleKeys(List<Finding> findings) {
        return findings.stream().map(f -> f.severity() + ":" + f.rule()).collect(Collectors.toSet());
    }

    static Stream<Arguments> taxonomy() {
        Expectation baseline = expectation("search_code", "Search the codebase.", CONSUMER_SCHEMA, List.of("tools"));
        ServerTool identical = new ServerTool("search_code", "Search the codebase.", json(CONSUMER_SCHEMA));

        return Stream.of(
                Arguments.of("clean — identical contract",
                        pact(baseline),
                        snapshot(List.of(identical), Set.of("tools")),
                        Set.of()),

                Arguments.of("BREAKING tool.missing — server no longer has the tool",
                        pact(baseline),
                        snapshot(List.of(), Set.of("tools")),
                        Set.of("BREAKING:tool.missing")),

                Arguments.of("BREAKING param.removed — a used param dropped",
                        pact(baseline),
                        snapshot(List.of(new ServerTool("search_code", "Search the codebase.",
                                json("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},"
                                        + "\"required\":[\"query\"]}"))), Set.of("tools")),
                        Set.of("BREAKING:param.removed")),

                Arguments.of("BREAKING param.type — a used param changed type",
                        pact(baseline),
                        snapshot(List.of(new ServerTool("search_code", "Search the codebase.",
                                json("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},"
                                        + "\"limit\":{\"type\":\"string\"}},\"required\":[\"query\"]}"))), Set.of("tools")),
                        Set.of("BREAKING:param.type")),

                Arguments.of("BREAKING param.newRequired — server requires a param the consumer omits",
                        pact(baseline),
                        snapshot(List.of(new ServerTool("search_code", "Search the codebase.",
                                json("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},"
                                        + "\"limit\":{\"type\":\"number\"},\"workspace\":{\"type\":\"string\"}},"
                                        + "\"required\":[\"query\",\"workspace\"]}"))), Set.of("tools")),
                        Set.of("BREAKING:param.newRequired")),

                Arguments.of("BREAKING capability.withdrawn — required capability gone",
                        pact(baseline),
                        snapshot(List.of(identical), Set.of()),
                        Set.of("BREAKING:capability.withdrawn")),

                Arguments.of("WARN tool.description — description drifted",
                        pact(baseline),
                        snapshot(List.of(new ServerTool("search_code",
                                "Search the codebase AND the web.", json(CONSUMER_SCHEMA))), Set.of("tools")),
                        Set.of("WARN:tool.description")),

                Arguments.of("COMPAT param.newOptional — new optional param added",
                        pact(baseline),
                        snapshot(List.of(new ServerTool("search_code", "Search the codebase.",
                                json("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},"
                                        + "\"limit\":{\"type\":\"number\"},\"verbose\":{\"type\":\"boolean\"}},"
                                        + "\"required\":[\"query\"]}"))), Set.of("tools")),
                        Set.of("COMPAT:param.newOptional")),

                Arguments.of("COMPAT tool.new — server offers an extra tool",
                        pact(baseline),
                        snapshot(List.of(identical,
                                new ServerTool("write_file", "Write a file.", json("{\"type\":\"object\"}"))),
                                Set.of("tools")),
                        Set.of("COMPAT:tool.new")),

                Arguments.of("mixed — a breaking removal alongside a compatible addition",
                        pact(baseline),
                        snapshot(List.of(new ServerTool("search_code", "Search the codebase.",
                                json("{\"type\":\"object\",\"properties\":{\"limit\":{\"type\":\"number\"},"
                                        + "\"verbose\":{\"type\":\"boolean\"}},\"required\":[\"limit\"]}"))),
                                Set.of("tools")),
                        // query removed (BREAKING), limit now required but consumer sends it (ok),
                        // verbose new optional (COMPAT)
                        Set.of("BREAKING:param.removed", "COMPAT:param.newOptional"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("taxonomy")
    void classifiesDrift(String label, Pact pact, ServerSnapshot snapshot, Set<String> expected) {
        assertThat(ruleKeys(ContractVerifier.diff(pact, snapshot))).isEqualTo(expected);
    }
}
