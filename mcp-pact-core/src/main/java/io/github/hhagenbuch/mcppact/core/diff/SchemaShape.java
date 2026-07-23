package io.github.hhagenbuch.mcppact.core.diff;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.hhagenbuch.mcppact.core.Finding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

    /**
     * Classifies the input-schema drift between a {@code consumer} shape (what a
     * pact recorded it sends) and a {@code provider} shape (what a server now
     * accepts), returning one {@link Finding} per material difference. This is the
     * single source of truth for the schema-diff rules; {@code ContractVerifier}
     * delegates to it. Downstream callers can classify any DTO/schema change
     * directly by building two shapes with {@link #of(JsonNode)}.
     *
     * <p>The rules, in order:
     * <ul>
     *   <li>consumer property the provider no longer has → BREAKING {@code param.removed}</li>
     *   <li>both plain string types present and differing → BREAKING {@code param.type}</li>
     *   <li>types agree (or aren't plain strings) but the subtree changed → WARN {@code param.schemaDetails}</li>
     *   <li>provider now requires a property the consumer omits → BREAKING {@code param.newRequired}</li>
     *   <li>provider added an optional property the consumer lacks → COMPAT {@code param.newOptional}</li>
     * </ul>
     *
     * @param tool     the tool the findings concern; fills {@link Finding#tool()}
     * @param consumer the shape the consumer recorded it sends
     * @param provider the shape the provider now accepts
     * @return the classified differences (empty when the shapes are equivalent)
     */
    public static List<Finding> diff(String tool, SchemaShape consumer, SchemaShape provider) {
        List<Finding> findings = new ArrayList<>();

        for (String param : consumer.properties().keySet()) {
            if (!provider.has(param)) {
                findings.add(Finding.breaking(tool, "param.removed",
                        "input param '" + param + "' the consumer sends is no longer accepted"));
                continue;
            }
            String consumerType = consumer.textualType(param);
            String providerType = provider.textualType(param);
            if (consumerType != null && providerType != null && !consumerType.equals(providerType)) {
                findings.add(Finding.breaking(tool, "param.type",
                        "input param '" + param + "' type changed from " + consumerType + " to " + providerType));
            } else if (!consumer.schema(param).equals(provider.schema(param))) {
                // Shallow types agree (or aren't plain strings) but the schema subtree
                // changed in a way we don't model precisely — flag rather than silence.
                findings.add(Finding.warn(tool, "param.schemaDetails",
                        "input param '" + param + "' schema changed in a way mcp-pact does not model precisely "
                                + "(nullable type, enum, pattern, length, or nested change) — review manually"));
            }
        }

        for (String required : provider.required()) {
            if (!consumer.has(required)) {
                findings.add(Finding.breaking(tool, "param.newRequired",
                        "server now requires input param '" + required + "' the consumer does not send"));
            }
        }

        for (String param : provider.properties().keySet()) {
            if (!consumer.has(param) && !provider.required().contains(param)) {
                findings.add(Finding.compat(tool, "param.newOptional",
                        "server added optional input param '" + param + "'"));
            }
        }

        return findings;
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
