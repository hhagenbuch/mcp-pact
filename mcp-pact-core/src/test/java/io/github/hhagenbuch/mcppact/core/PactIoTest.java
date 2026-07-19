package io.github.hhagenbuch.mcppact.core;

import io.github.hhagenbuch.mcppact.core.model.Expectation;
import io.github.hhagenbuch.mcppact.core.model.Matcher;
import io.github.hhagenbuch.mcppact.core.model.Pact;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PactIoTest {

    private static final String PACT = """
            {
              "pactVersion": "0.1",
              "consumer": "support-agent",
              "provider": "workspace-tools-mcp",
              "expectations": [
                {
                  "tool": "search_code",
                  "description": "Search the codebase.",
                  "inputSchema": {
                    "type": "object",
                    "properties": { "query": { "type": "string" }, "limit": { "type": "number" } },
                    "required": ["query"]
                  },
                  "requiredCapabilities": ["tools"],
                  "interactions": [
                    {
                      "description": "search by symbol name",
                      "input": { "query": "CartService", "limit": 5 },
                      "response": {
                        "matchers": [
                          { "path": "$.content[0].type", "equals": "text" },
                          { "path": "$.content[0].text", "regex": "CartService" },
                          { "path": "$.isError", "equals": false }
                        ]
                      }
                    }
                  ]
                }
              ]
            }
            """;

    @Test
    void parsesTheDocumentedShape() {
        Pact pact = PactIO.parse(PACT);
        assertThat(pact.pactVersion()).isEqualTo("0.1");
        assertThat(pact.consumer()).isEqualTo("support-agent");
        assertThat(pact.provider()).isEqualTo("workspace-tools-mcp");
        assertThat(pact.expectations()).hasSize(1);

        Expectation expectation = pact.expectations().get(0);
        assertThat(expectation.tool()).isEqualTo("search_code");
        assertThat(expectation.description()).isEqualTo("Search the codebase.");
        assertThat(expectation.requiredCapabilities()).containsExactly("tools");
        assertThat(expectation.interactions()).hasSize(1);

        var matchers = expectation.interactions().get(0).response().matchers();
        assertThat(matchers).extracting(Matcher::kind)
                .containsExactly(Matcher.Kind.EQUALS, Matcher.Kind.REGEX, Matcher.Kind.EQUALS);
    }

    @Test
    void roundTripsThroughJson() {
        Pact pact = PactIO.parse(PACT);
        Pact reparsed = PactIO.parse(PactIO.toJson(pact));
        assertThat(reparsed.consumer()).isEqualTo(pact.consumer());
        assertThat(reparsed.expectations().get(0).interactions().get(0).response().matchers())
                .hasSize(3);
    }

    @Test
    void ignoresUnknownFields() {
        Pact pact = PactIO.parse("""
                { "pactVersion": "0.1", "consumer": "c", "provider": "p",
                  "expectations": [], "generatedBy": "mcp-pact-recorder/0.1", "extra": 42 }
                """);
        assertThat(pact.consumer()).isEqualTo("c");
        assertThat(pact.expectations()).isEmpty();
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> PactIO.parse("{ not json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
