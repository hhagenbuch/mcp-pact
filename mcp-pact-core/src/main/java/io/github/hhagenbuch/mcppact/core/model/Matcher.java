package io.github.hhagenbuch.mcppact.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A single response assertion on a JSON path. Exactly one predicate
 * ({@code equals}, {@code regex}, {@code type}, {@code present}) is set;
 * {@code equals} is named {@code equalsValue} in Java to avoid clashing with
 * {@link Object#equals}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Matcher(
        String path,
        @JsonProperty("equals") JsonNode equalsValue,
        String regex,
        String type,
        Boolean present) {

    public enum Kind { EQUALS, REGEX, TYPE, PRESENT }

    /** The single predicate this matcher carries. */
    public Kind kind() {
        if (equalsValue != null) return Kind.EQUALS;
        if (regex != null) return Kind.REGEX;
        if (type != null) return Kind.TYPE;
        if (present != null) return Kind.PRESENT;
        throw new IllegalStateException("matcher on '" + path + "' has no predicate");
    }
}
