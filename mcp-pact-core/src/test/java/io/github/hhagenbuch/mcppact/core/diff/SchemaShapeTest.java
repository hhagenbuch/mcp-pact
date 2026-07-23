package io.github.hhagenbuch.mcppact.core.diff;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.hhagenbuch.mcppact.core.Finding;
import io.github.hhagenbuch.mcppact.core.PactIO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reusable, public {@link SchemaShape#diff} classifier, exercised shape-vs-shape
 * (no pact/snapshot wrapping). This is the surface a downstream consumer classifies
 * DTO/schema changes through; each case pins one rule to its {@code SEVERITY:rule} key.
 */
class SchemaShapeTest {

    private static SchemaShape shape(String schema) {
        try {
            JsonNode node = PactIO.mapper().readTree(schema);
            return SchemaShape.of(node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A consumer that sends {query:string, limit:number}, requiring `query`.
    private static final String CONSUMER =
            "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},"
                    + "\"limit\":{\"type\":\"number\"}},\"required\":[\"query\"]}";

    private static List<String> ruleKeys(List<Finding> findings) {
        return findings.stream()
                .map(f -> f.severity() + ":" + f.rule())
                .collect(Collectors.toList());
    }

    @Test
    void noChangeProducesNoFindings() {
        assertThat(SchemaShape.diff("search_code", shape(CONSUMER), shape(CONSUMER))).isEmpty();
    }

    @Test
    void removedConsumerParamIsBreaking() {
        String provider = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},"
                + "\"required\":[\"query\"]}";
        assertThat(ruleKeys(SchemaShape.diff("search_code", shape(CONSUMER), shape(provider))))
                .containsExactly("BREAKING:param.removed");
    }

    @Test
    void changedTextualTypeIsBreaking() {
        String provider = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},"
                + "\"limit\":{\"type\":\"string\"}},\"required\":[\"query\"]}";
        assertThat(ruleKeys(SchemaShape.diff("search_code", shape(CONSUMER), shape(provider))))
                .containsExactly("BREAKING:param.type");
    }

    @Test
    void subtreeChangeWithAgreeingTypesIsWarn() {
        // Shallow types agree (limit stays a number) but the subtree widened to a nullable array.
        String provider = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},"
                + "\"limit\":{\"type\":[\"number\",\"null\"]}},\"required\":[\"query\"]}";
        assertThat(ruleKeys(SchemaShape.diff("search_code", shape(CONSUMER), shape(provider))))
                .containsExactly("WARN:param.schemaDetails");
    }

    @Test
    void newlyRequiredParamTheConsumerOmitsIsBreaking() {
        String provider = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},"
                + "\"limit\":{\"type\":\"number\"},\"workspace\":{\"type\":\"string\"}},"
                + "\"required\":[\"query\",\"workspace\"]}";
        assertThat(ruleKeys(SchemaShape.diff("search_code", shape(CONSUMER), shape(provider))))
                .containsExactly("BREAKING:param.newRequired");
    }

    @Test
    void newOptionalParamIsCompat() {
        String provider = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},"
                + "\"limit\":{\"type\":\"number\"},\"verbose\":{\"type\":\"boolean\"}},"
                + "\"required\":[\"query\"]}";
        assertThat(ruleKeys(SchemaShape.diff("search_code", shape(CONSUMER), shape(provider))))
                .containsExactly("COMPAT:param.newOptional");
    }

    @Test
    void toolArgFillsTheFindingTool() {
        String provider = "{\"type\":\"object\",\"properties\":{}}";
        List<Finding> findings = SchemaShape.diff("my_tool", shape(CONSUMER), shape(provider));
        assertThat(findings).allSatisfy(f -> assertThat(f.tool()).isEqualTo("my_tool"));
    }
}
