package io.github.hhagenbuch.mcppact.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Everything a consumer depends on for one tool: its name, the description seen
 * at record time (for drift detection), the consumer-exercised subset of its
 * input schema, any required server capabilities, and the recorded interactions.
 *
 * @param tool                 tool name as used via {@code tools/call}
 * @param description          tool description observed at record time (nullable)
 * @param inputSchema          consumer-exercised subset of the input JSON Schema
 * @param requiredCapabilities MCP capabilities the consumer needs (nullable → none)
 * @param interactions         recorded calls and expected response shapes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Expectation(
        String tool,
        String description,
        JsonNode inputSchema,
        List<String> requiredCapabilities,
        List<Interaction> interactions) {

    public Expectation {
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
        interactions = interactions == null ? List.of() : List.copyOf(interactions);
    }
}
