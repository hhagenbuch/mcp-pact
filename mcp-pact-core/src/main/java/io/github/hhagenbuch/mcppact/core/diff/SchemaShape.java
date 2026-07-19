package io.github.hhagenbuch.mcppact.core.diff;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The subset of a JSON Schema object that the diff engine reasons about: the
 * declared properties (name → declared {@code type}, possibly null) and the set
 * of required property names. Deliberately shallow — MVP compares top-level
 * object properties only; nested schemas are a roadmap item.
 */
public record SchemaShape(Map<String, String> propertyTypes, Set<String> required) {

    public static SchemaShape of(JsonNode schema) {
        Map<String, String> types = new LinkedHashMap<>();
        Set<String> required = new LinkedHashSet<>();
        if (schema != null && schema.isObject()) {
            JsonNode properties = schema.get("properties");
            if (properties != null && properties.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> field = it.next();
                    JsonNode type = field.getValue().get("type");
                    types.put(field.getKey(), type != null && type.isTextual() ? type.asText() : null);
                }
            }
            JsonNode req = schema.get("required");
            if (req != null && req.isArray()) {
                req.forEach(n -> required.add(n.asText()));
            }
        }
        return new SchemaShape(types, required);
    }

    public boolean has(String property) {
        return propertyTypes.containsKey(property);
    }

    public String type(String property) {
        return propertyTypes.get(property);
    }
}
