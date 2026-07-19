package io.github.hhagenbuch.mcppact.core.matcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hhagenbuch.mcppact.core.Finding;
import io.github.hhagenbuch.mcppact.core.Severity;
import io.github.hhagenbuch.mcppact.core.model.Matcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatcherEngineTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode response() throws Exception {
        return mapper.readTree(
                "{\"content\":[{\"type\":\"text\",\"text\":\"found CartService\"}],\"isError\":false}");
    }

    private List<Finding> eval(Matcher matcher) throws Exception {
        return MatcherEngine.evaluate("search_code", List.of(matcher), response());
    }

    private Matcher equalsM(String path, String literalJson) throws Exception {
        return new Matcher(path, mapper.readTree(literalJson), null, null, null);
    }

    // ---- passing matchers produce no findings ----

    @Test
    void equalsPasses() throws Exception {
        assertThat(eval(equalsM("$.isError", "false"))).isEmpty();
    }

    @Test
    void regexPasses() throws Exception {
        assertThat(eval(new Matcher("$.content[0].text", null, "CartService", null, null))).isEmpty();
    }

    @Test
    void typePasses() throws Exception {
        assertThat(eval(new Matcher("$.content[0].text", null, null, "string", null))).isEmpty();
    }

    @Test
    void presentTruePasses() throws Exception {
        assertThat(eval(new Matcher("$.content[0].text", null, null, null, true))).isEmpty();
    }

    @Test
    void presentFalsePassesWhenAbsent() throws Exception {
        assertThat(eval(new Matcher("$.stackTrace", null, null, null, false))).isEmpty();
    }

    // ---- failing matchers are BREAKING ----

    @Test
    void equalsMismatchIsBreaking() throws Exception {
        assertThat(eval(equalsM("$.isError", "true"))).singleElement()
                .extracting(Finding::severity).isEqualTo(Severity.BREAKING);
    }

    @Test
    void equalsMissingPathIsBreaking() throws Exception {
        assertThat(eval(equalsM("$.nope", "true"))).singleElement()
                .extracting(Finding::severity).isEqualTo(Severity.BREAKING);
    }

    @Test
    void regexMismatchIsBreaking() throws Exception {
        assertThat(eval(new Matcher("$.content[0].text", null, "OrderService", null, null))).singleElement()
                .extracting(Finding::severity).isEqualTo(Severity.BREAKING);
    }

    @Test
    void regexOnNonStringIsBreaking() throws Exception {
        assertThat(eval(new Matcher("$.isError", null, "true", null, null))).singleElement()
                .extracting(Finding::severity).isEqualTo(Severity.BREAKING);
    }

    @Test
    void typeMismatchIsBreaking() throws Exception {
        assertThat(eval(new Matcher("$.isError", null, null, "string", null))).singleElement()
                .extracting(Finding::severity).isEqualTo(Severity.BREAKING);
    }

    @Test
    void presentTrueMissingIsBreaking() throws Exception {
        assertThat(eval(new Matcher("$.nope", null, null, null, true))).singleElement()
                .extracting(Finding::severity).isEqualTo(Severity.BREAKING);
    }

    @Test
    void presentFalseButPresentIsBreaking() throws Exception {
        assertThat(eval(new Matcher("$.isError", null, null, null, false))).singleElement()
                .extracting(Finding::severity).isEqualTo(Severity.BREAKING);
    }
}
