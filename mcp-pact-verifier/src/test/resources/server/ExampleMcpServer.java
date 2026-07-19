import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tiny stdio MCP server for the verifier's e2e tests, mutated by args[0] so a
 * single file can play the "provider drifted" role for each taxonomy class:
 *
 *   (none)/v1  a clean provider matching the consumer pact
 *   rename     the tool is renamed            -> BREAKING tool.missing
 *   retype     a used param changes type      -> BREAKING param.type
 *   require    a new required param appears    -> BREAKING param.newRequired
 *   desc       the description changes         -> WARN   tool.description
 *   breakresp  tools/call returns a bad shape  -> BREAKING response.matcher (on replay)
 *
 * Newline-delimited JSON-RPC 2.0, JDK-only, run via `java ExampleMcpServer.java [mode]`.
 */
public class ExampleMcpServer {

    private static final Pattern METHOD = Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern QUERY = Pattern.compile("\"query\"\\s*:\\s*\"([^\"]*)\"");

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "v1";
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String method = group(METHOD, line);
            String id = group(ID, line);
            if (method == null) {
                continue;
            }
            switch (method) {
                case "initialize" -> reply(id, "{\"protocolVersion\":\"2024-11-05\","
                        + "\"capabilities\":{\"tools\":{}},"
                        + "\"serverInfo\":{\"name\":\"example\",\"version\":\"0.0.1\"}}");
                case "notifications/initialized" -> { /* notification */ }
                case "tools/list" -> reply(id, "{\"tools\":[" + toolDefinition(mode) + "]}");
                case "tools/call" -> reply(id, callResult(mode, group(QUERY, line)));
                default -> { /* ignore */ }
            }
        }
    }

    private static String toolDefinition(String mode) {
        String name = mode.equals("rename") ? "search" : "search_code";
        String description = mode.equals("desc")
                ? "Search the codebase AND the public web."
                : "Search the codebase.";
        String limitType = mode.equals("retype") ? "string" : "number";
        String properties = "\"query\":{\"type\":\"string\"},\"limit\":{\"type\":\"" + limitType + "\"}";
        String required = "[\"query\"]";
        if (mode.equals("require")) {
            properties += ",\"workspace\":{\"type\":\"string\"}";
            required = "[\"query\",\"workspace\"]";
        }
        return "{\"name\":\"" + name + "\","
                + "\"description\":\"" + description + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{" + properties + "},"
                + "\"required\":" + required + "}}";
    }

    private static String callResult(String mode, String query) {
        String q = query == null ? "" : query;
        if (mode.equals("breakresp")) {
            return "{\"content\":[{\"type\":\"text\",\"text\":\"internal error\"}],\"isError\":true}";
        }
        return "{\"content\":[{\"type\":\"text\",\"text\":\"found " + q + " in the codebase\"}],\"isError\":false}";
    }

    private static void reply(String id, String result) {
        System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + result + "}");
        System.out.flush();
    }

    private static String group(Pattern pattern, String line) {
        Matcher m = pattern.matcher(line);
        return m.find() ? m.group(1) : null;
    }
}
