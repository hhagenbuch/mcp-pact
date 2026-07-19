package io.github.hhagenbuch.mcppact.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonPathsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    @Test
    void resolvesRootFieldsAndArrayIndexes() throws Exception {
        JsonNode root = json("{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}],\"isError\":false}");
        assertThat(JsonPaths.resolve(root, "$")).contains(root);
        assertThat(JsonPaths.resolve(root, "$.isError").get().asBoolean()).isFalse();
        assertThat(JsonPaths.resolve(root, "$.content[0].type").get().asText()).isEqualTo("text");
        assertThat(JsonPaths.resolve(root, "$.content[0].text").get().asText()).isEqualTo("hi");
    }

    @Test
    void returnsEmptyForMissingSteps() throws Exception {
        JsonNode root = json("{\"content\":[{\"text\":\"hi\"}]}");
        assertThat(JsonPaths.resolve(root, "$.nope")).isEmpty();          // missing field
        assertThat(JsonPaths.resolve(root, "$.content[5]")).isEmpty();    // index out of range
        assertThat(JsonPaths.resolve(root, "$.content[0].type")).isEmpty(); // missing nested field
        assertThat(JsonPaths.resolve(root, "$.isError[0]")).isEmpty();    // index into non-array
    }

    @Test
    void rejectsMalformedPaths() {
        assertThatThrownBy(() -> JsonPaths.resolve(mapper.createObjectNode(), "content"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonPaths.resolve(mapper.createObjectNode(), "$.a..b"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
