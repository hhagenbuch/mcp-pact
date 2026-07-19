package io.github.hhagenbuch.mcppact.recorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hhagenbuch.mcppact.core.model.Expectation;
import io.github.hhagenbuch.mcppact.core.model.Matcher;
import io.github.hhagenbuch.mcppact.core.model.Pact;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecorderSessionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    private RecorderSession sessionWithOneCall() throws Exception {
        RecorderSession session = new RecorderSession();
        session.observeClientMessage(json("{\"id\":1,\"method\":\"initialize\"}"));
        session.observeServerMessage(json("{\"id\":1,\"result\":{\"capabilities\":{\"tools\":{}}}}"));
        session.observeClientMessage(json("{\"id\":2,\"method\":\"tools/list\"}"));
        session.observeServerMessage(json("{\"id\":2,\"result\":{\"tools\":[{"
                + "\"name\":\"search_code\",\"description\":\"Search the codebase.\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{"
                + "\"query\":{\"type\":\"string\"},\"limit\":{\"type\":\"number\"},"
                + "\"scope\":{\"type\":\"string\"}},\"required\":[\"query\"]}}]}}"));
        session.observeClientMessage(json("{\"id\":3,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"search_code\",\"arguments\":{\"query\":\"CartService\",\"limit\":5}}}"));
        session.observeServerMessage(json("{\"id\":3,\"result\":{"
                + "\"content\":[{\"type\":\"text\",\"text\":\"found CartService\"}],\"isError\":false}}"));
        return session;
    }

    @Test
    void recordsToolDescriptionAndCapabilities() throws Exception {
        Pact pact = sessionWithOneCall().toPact("support-agent", "example-tools");
        assertThat(pact.expectations()).hasSize(1);
        Expectation expectation = pact.expectations().get(0);
        assertThat(expectation.tool()).isEqualTo("search_code");
        assertThat(expectation.description()).isEqualTo("Search the codebase.");
        assertThat(expectation.requiredCapabilities()).containsExactly("tools");
    }

    @Test
    void capturesOnlyTheConsumerExercisedSchemaSubset() throws Exception {
        Expectation expectation = sessionWithOneCall().toPact("c", "p").expectations().get(0);
        JsonNode properties = expectation.inputSchema().path("properties");
        // The server advertises query, limit AND scope — but the client only sent query + limit.
        assertThat(properties.has("query")).isTrue();
        assertThat(properties.has("limit")).isTrue();
        assertThat(properties.has("scope")).isFalse();
        // types come from the server's advertised schema
        assertThat(properties.path("query").path("type").asText()).isEqualTo("string");
        assertThat(properties.path("limit").path("type").asText()).isEqualTo("number");
    }

    @Test
    void derivesShapeMatchersFromTheResponse() throws Exception {
        Expectation expectation = sessionWithOneCall().toPact("c", "p").expectations().get(0);
        var matchers = expectation.interactions().get(0).response().matchers();
        // isError equals false, content[0].type equals text, content[0].text is a string
        assertThat(matchers).extracting(Matcher::path)
                .containsExactly("$.isError", "$.content[0].type", "$.content[0].text");
        assertThat(matchers).extracting(Matcher::kind)
                .containsExactly(Matcher.Kind.EQUALS, Matcher.Kind.EQUALS, Matcher.Kind.TYPE);
    }

    @Test
    void correlatesStringJsonRpcIds() throws Exception {
        // The JSON-RPC spec allows string ids; correlation must not assume numbers.
        RecorderSession session = new RecorderSession();
        session.observeClientMessage(json("{\"id\":\"call-abc\",\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"echo\",\"arguments\":{\"text\":\"hi\"}}}"));
        session.observeServerMessage(json("{\"id\":\"call-abc\",\"result\":{"
                + "\"content\":[{\"type\":\"text\",\"text\":\"hi\"}],\"isError\":false}}"));

        Pact pact = session.toPact("c", "p");
        assertThat(pact.expectations()).hasSize(1);
        assertThat(pact.expectations().get(0).tool()).isEqualTo("echo");
        assertThat(pact.expectations().get(0).interactions()).hasSize(1);
    }

    @Test
    void infersTypesWhenServerSchemaUnavailable() throws Exception {
        // No tools/list observed → types inferred from the values the client sent.
        RecorderSession session = new RecorderSession();
        session.observeClientMessage(json("{\"id\":1,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"echo\",\"arguments\":{\"text\":\"hi\",\"count\":3}}}"));
        session.observeServerMessage(json("{\"id\":1,\"result\":{"
                + "\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}}"));
        JsonNode props = session.toPact("c", "p").expectations().get(0).inputSchema().path("properties");
        assertThat(props.path("text").path("type").asText()).isEqualTo("string");
        assertThat(props.path("count").path("type").asText()).isEqualTo("number");
    }
}
