package io.github.hhagenbuch.mcppact.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A consumer-driven contract: what {@code consumer} depends on from
 * {@code provider}. Deserialized from a {@code *.mcp-pact.json} file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Pact(
        String pactVersion,
        String consumer,
        String provider,
        List<Expectation> expectations) {

    public Pact {
        expectations = expectations == null ? List.of() : List.copyOf(expectations);
    }
}
