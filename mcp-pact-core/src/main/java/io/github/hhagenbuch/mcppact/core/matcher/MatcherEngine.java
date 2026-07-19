package io.github.hhagenbuch.mcppact.core.matcher;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.hhagenbuch.mcppact.core.Finding;
import io.github.hhagenbuch.mcppact.core.JsonPaths;
import io.github.hhagenbuch.mcppact.core.model.Matcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Checks a recorded {@link Matcher} set against an actual tool response. A
 * failing matcher is a {@code BREAKING} finding — the response shape the
 * consumer relied on no longer holds.
 */
public final class MatcherEngine {

    private MatcherEngine() {
    }

    public static List<Finding> evaluate(String tool, List<Matcher> matchers, JsonNode response) {
        List<Finding> findings = new ArrayList<>();
        for (Matcher matcher : matchers) {
            check(tool, matcher, response).ifPresent(findings::add);
        }
        return findings;
    }

    private static Optional<Finding> check(String tool, Matcher matcher, JsonNode response) {
        Optional<JsonNode> at = JsonPaths.resolve(response, matcher.path());
        return switch (matcher.kind()) {
            case PRESENT -> {
                boolean wantPresent = matcher.present();
                if (at.isPresent() == wantPresent) {
                    yield Optional.empty();
                }
                yield fail(tool, matcher, wantPresent ? "expected path to be present" : "expected path to be absent");
            }
            case EQUALS -> at.isEmpty()
                    ? fail(tool, matcher, "path missing")
                    : (jsonEquals(at.get(), matcher.equalsValue())
                    ? Optional.empty()
                    : fail(tool, matcher, "expected " + matcher.equalsValue() + " but was " + at.get()));
            case REGEX -> {
                if (at.isEmpty()) {
                    yield fail(tool, matcher, "path missing");
                }
                JsonNode node = at.get();
                if (!node.isTextual()) {
                    yield fail(tool, matcher, "expected a string to match /" + matcher.regex() + "/ but was " + node);
                }
                yield Pattern.compile(matcher.regex()).matcher(node.asText()).find()
                        ? Optional.empty()
                        : fail(tool, matcher, "value \"" + node.asText() + "\" does not match /" + matcher.regex() + "/");
            }
            case TYPE -> at.isEmpty()
                    ? fail(tool, matcher, "path missing")
                    : (typeMatches(matcher.type(), at.get())
                    ? Optional.empty()
                    : fail(tool, matcher, "expected type " + matcher.type() + " but was " + jsonType(at.get())));
        };
    }

    private static Optional<Finding> fail(String tool, Matcher matcher, String reason) {
        return Optional.of(Finding.breaking(tool, "response.matcher",
                matcher.path() + ": " + reason));
    }

    /**
     * Equality that compares numbers by value, so a server switching an integer
     * to its float representation ({@code 4} → {@code 4.0}) is not a false
     * BREAKING. All other node types fall back to structural equality.
     */
    private static boolean jsonEquals(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        }
        return a.equals(b);
    }

    private static boolean typeMatches(String type, JsonNode node) {
        return switch (type) {
            case "string" -> node.isTextual();
            case "number" -> node.isNumber();
            case "boolean" -> node.isBoolean();
            case "object" -> node.isObject();
            case "array" -> node.isArray();
            case "null" -> node.isNull();
            default -> throw new IllegalArgumentException("unknown matcher type: " + type);
        };
    }

    private static String jsonType(JsonNode node) {
        if (node.isTextual()) return "string";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isNull()) return "null";
        return node.getNodeType().toString().toLowerCase();
    }
}
