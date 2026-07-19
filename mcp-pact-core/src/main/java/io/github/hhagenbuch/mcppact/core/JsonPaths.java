package io.github.hhagenbuch.mcppact.core;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the small JSONPath subset used by matchers: a leading {@code $},
 * dot-separated object fields ({@code $.isError}), and array indexes
 * ({@code $.content[0].text}). The path is fully tokenized (and validated)
 * before navigation, so a malformed path is a usage error regardless of the
 * data it is run against.
 */
public final class JsonPaths {

    private static final Pattern TOKEN = Pattern.compile("\\.([^.\\[\\]]+)|\\[(\\d+)\\]");

    private JsonPaths() {
    }

    /** A path step: an object field name or an array index. */
    private sealed interface Step {
        record Field(String name) implements Step {}
        record Index(int index) implements Step {}
    }

    /** Returns the node at {@code path}, or empty if any step is missing. */
    public static Optional<JsonNode> resolve(JsonNode root, String path) {
        List<Step> steps = parse(path);
        JsonNode current = root;
        for (Step step : steps) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return Optional.empty();
            }
            if (step instanceof Step.Field field) {
                if (!current.isObject() || !current.has(field.name())) {
                    return Optional.empty();
                }
                current = current.get(field.name());
            } else {
                int index = ((Step.Index) step).index();
                if (!current.isArray() || index >= current.size()) {
                    return Optional.empty();
                }
                current = current.get(index);
            }
        }
        return Optional.ofNullable(current);
    }

    private static List<Step> parse(String path) {
        if (path == null || !path.startsWith("$")) {
            throw new IllegalArgumentException("path must start with '$': " + path);
        }
        String rest = path.substring(1);
        List<Step> steps = new ArrayList<>();
        Matcher m = TOKEN.matcher(rest);
        int consumed = 0;
        while (m.find()) {
            if (m.start() != consumed) {
                throw new IllegalArgumentException("malformed path near index " + (consumed + 1) + ": " + path);
            }
            consumed = m.end();
            if (m.group(1) != null) {
                steps.add(new Step.Field(m.group(1)));
            } else {
                steps.add(new Step.Index(Integer.parseInt(m.group(2))));
            }
        }
        if (consumed != rest.length()) {
            throw new IllegalArgumentException("malformed path: " + path);
        }
        return steps;
    }
}
