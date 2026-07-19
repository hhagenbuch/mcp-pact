package io.github.hhagenbuch.mcppact.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** The expected shape of a tool response: a set of path matchers. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResponseSpec(List<Matcher> matchers) {

    public ResponseSpec {
        matchers = matchers == null ? List.of() : List.copyOf(matchers);
    }
}
