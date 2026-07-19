package io.github.hhagenbuch.mcppact.recorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.mcppact.core.Capabilities;
import io.github.hhagenbuch.mcppact.core.model.Expectation;
import io.github.hhagenbuch.mcppact.core.model.Interaction;
import io.github.hhagenbuch.mcppact.core.model.Matcher;
import io.github.hhagenbuch.mcppact.core.model.Pact;
import io.github.hhagenbuch.mcppact.core.model.ResponseSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Observes both directions of an MCP session (client requests, server responses)
 * and distils them into a {@link Pact}. Pure and thread-confined — the proxy
 * feeds it messages in order; it does no I/O itself.
 *
 * <p>The point of interest is <em>consumer-exercised</em> schema capture: an
 * expectation's {@code inputSchema} records only the fields the client actually
 * sent (typed from the server's advertised schema when available, else inferred
 * from the value), not the server's full advertised schema. That is what makes
 * the contract encode the consumer's real dependency surface.
 */
public final class RecorderSession {

    private record ServerTool(String description, JsonNode inputSchema) {}

    private record Call(JsonNode arguments, JsonNode result) {}

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Integer, JsonNode> pending = new LinkedHashMap<>();
    private final Map<String, ServerTool> serverTools = new LinkedHashMap<>();
    private final Map<String, List<Call>> callsByTool = new LinkedHashMap<>();
    private final Set<String> capabilities = new LinkedHashSet<>();

    /** A message the client sent toward the server. */
    public void observeClientMessage(JsonNode message) {
        if (message.has("id") && message.has("method")) {
            pending.put(message.get("id").asInt(), message);
        }
    }

    /** A message the server sent back to the client. */
    public void observeServerMessage(JsonNode message) {
        if (!message.has("id")) {
            return;
        }
        JsonNode request = pending.remove(message.get("id").asInt());
        if (request == null || !message.has("result")) {
            return;
        }
        JsonNode result = message.get("result");
        switch (request.path("method").asText()) {
            case "initialize" -> capabilities.addAll(Capabilities.flatten(result.path("capabilities")));
            case "tools/list" -> {
                for (JsonNode tool : result.path("tools")) {
                    serverTools.put(tool.path("name").asText(), new ServerTool(
                            tool.hasNonNull("description") ? tool.path("description").asText() : null,
                            tool.path("inputSchema")));
                }
            }
            case "tools/call" -> {
                String tool = request.path("params").path("name").asText();
                JsonNode arguments = request.path("params").path("arguments");
                callsByTool.computeIfAbsent(tool, t -> new ArrayList<>()).add(new Call(arguments, result));
            }
            default -> { /* other methods are forwarded but not recorded */ }
        }
    }

    public Pact toPact(String consumer, String provider) {
        List<Expectation> expectations = new ArrayList<>();
        for (Map.Entry<String, List<Call>> entry : callsByTool.entrySet()) {
            String tool = entry.getKey();
            List<Call> calls = entry.getValue();
            ServerTool advertised = serverTools.get(tool);

            expectations.add(new Expectation(
                    tool,
                    advertised != null ? advertised.description() : null,
                    exercisedSchema(calls, advertised),
                    capabilities.contains("tools") ? List.of("tools") : List.of(),
                    interactions(tool, calls)));
        }
        return new Pact("0.1", consumer, provider, expectations);
    }

    /** Builds an input schema from only the fields the client sent across its calls. */
    private ObjectNode exercisedSchema(List<Call> calls, ServerTool advertised) {
        // Fields sent in EVERY call are treated as required; the union are the properties.
        Set<String> union = new LinkedHashSet<>();
        Set<String> required = null;
        for (Call call : calls) {
            Set<String> fields = fieldNames(call.arguments());
            union.addAll(fields);
            required = required == null ? new LinkedHashSet<>(fields) : intersect(required, fields);
        }

        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        for (String field : union) {
            properties.set(field, propertySchema(field, calls, advertised));
        }
        if (required != null && !required.isEmpty()) {
            ArrayNode req = schema.putArray("required");
            required.forEach(req::add);
        }
        return schema;
    }

    private ObjectNode propertySchema(String field, List<Call> calls, ServerTool advertised) {
        // Prefer the server's advertised type; else infer from the first value the client sent.
        String advertisedType = advertisedType(advertised, field);
        String type = advertisedType != null ? advertisedType : inferredType(field, calls);
        ObjectNode prop = mapper.createObjectNode();
        if (type != null) {
            prop.put("type", type);
        }
        return prop;
    }

    private String advertisedType(ServerTool advertised, String field) {
        if (advertised == null) {
            return null;
        }
        JsonNode type = advertised.inputSchema().path("properties").path(field).path("type");
        return type.isTextual() ? type.asText() : null;
    }

    private String inferredType(String field, List<Call> calls) {
        for (Call call : calls) {
            JsonNode value = call.arguments().path(field);
            if (!value.isMissingNode()) {
                return jsonType(value);
            }
        }
        return null;
    }

    private List<Interaction> interactions(String tool, List<Call> calls) {
        List<Interaction> interactions = new ArrayList<>();
        int n = 1;
        for (Call call : calls) {
            interactions.add(new Interaction(
                    "recorded call " + n++ + " to " + tool,
                    call.arguments(),
                    new ResponseSpec(matchers(call.result()))));
        }
        return interactions;
    }

    /**
     * Derives shape-capturing matchers from a recorded response: assert the error
     * flag and the first content block's type by value, and the text is present
     * as a string. Values likely to be nondeterministic (the text itself) are
     * captured by type, not by literal — leaving the user to tighten with a regex.
     */
    private List<Matcher> matchers(JsonNode result) {
        List<Matcher> matchers = new ArrayList<>();
        if (result.path("isError").isBoolean()) {
            matchers.add(new Matcher("$.isError", result.get("isError"), null, null, null));
        }
        JsonNode firstBlock = result.path("content").path(0);
        if (firstBlock.path("type").isTextual()) {
            matchers.add(new Matcher("$.content[0].type", firstBlock.get("type"), null, null, null));
        }
        if (firstBlock.path("text").isTextual()) {
            matchers.add(new Matcher("$.content[0].text", null, null, "string", null));
        }
        return matchers;
    }

    private Set<String> fieldNames(JsonNode object) {
        Set<String> names = new LinkedHashSet<>();
        if (object != null && object.isObject()) {
            object.fieldNames().forEachRemaining(names::add);
        }
        return names;
    }

    private Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.retainAll(b);
        return out;
    }

    private String jsonType(JsonNode node) {
        if (node.isTextual()) return "string";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isNull()) return "null";
        return null;
    }
}
