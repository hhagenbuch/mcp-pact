package io.github.hhagenbuch.mcppact.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * One recorded call: the input the consumer sent and the response shape it
 * relied on.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Interaction(String description, JsonNode input, ResponseSpec response) {
}
