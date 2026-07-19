package io.github.hhagenbuch.mcppact.core.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A tool as currently advertised by a provider's {@code tools/list}: its name,
 * description, and full input JSON Schema. The verifier builds these from a live
 * server; {@code mcp-pact-core} only compares them against a {@link Pact}.
 */
public record ServerTool(String name, String description, JsonNode inputSchema) {
}
