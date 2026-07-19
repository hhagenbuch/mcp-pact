package io.github.hhagenbuch.mcppact.core;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Flattens an MCP {@code capabilities} object into the set of dotted paths it
 * declares, so a pact can require either a top-level capability ({@code "tools"})
 * or a nested one ({@code "resources.subscribe"}) with no format change later.
 *
 * <p>{@code {"tools":{}, "resources":{"subscribe":true}}} →
 * {@code {"tools", "resources", "resources.subscribe"}}.
 */
public final class Capabilities {

    private Capabilities() {
    }

    public static Set<String> flatten(JsonNode capabilities) {
        Set<String> paths = new LinkedHashSet<>();
        walk("", capabilities, paths);
        return paths;
    }

    private static void walk(String prefix, JsonNode node, Set<String> out) {
        if (node == null || !node.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            String path = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
            out.add(path);
            walk(path, field.getValue(), out);
        }
    }
}
