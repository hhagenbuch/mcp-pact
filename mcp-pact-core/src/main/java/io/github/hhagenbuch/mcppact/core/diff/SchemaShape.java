package io.github.hhagenbuch.mcppact.core.diff;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The part of a JSON Schema object the diff reasons about: each property's full
 * schema subtree and the set of required property names. Keeping the whole
 * subtree (not just a type string) lets the diff detect changes it does not
 * model precisely — nullable type arrays, enums, patterns, nested objects — and
 * flag them rather than silently blessing them.
 */
public record SchemaShape(Map<String, JsonNode> properties, Set<String> required) {

    public static SchemaShape of(JsonNode schema) {
        Map<String, JsonNode> properties = new LinkedHashMap<>();
        Set<String> required = new LinkedHashSet<>();
        if (schema != null && schema.isObject()) {
            JsonNode props = schema.get("properties");
            if (props != null && props.isObject()) {
                for (Map.Entry<String, JsonNode> field : props.properties()) {
                    properties.put(field.getKey(), field.getValue());
                }
            }
            JsonNode req = schema.get("required");
            if (req != null && req.isArray()) {
                req.forEach(n -> required.add(n.asText()));
            }
        }
        return new SchemaShape(properties, required);
    }

    public boolean has(String property) {
        return properties.containsKey(property);
    }

    public JsonNode schema(String property) {
        return properties.get(property);
    }

    /** The declared {@code type} if it is a plain string, else null (absent, or a type array). */
    public String textualType(String property) {
        JsonNode s = properties.get(property);
        if (s == null) {
            return null;
        }
        JsonNode type = s.get("type");
        return type != null && type.isTextual() ? type.asText() : null;
    }
}
