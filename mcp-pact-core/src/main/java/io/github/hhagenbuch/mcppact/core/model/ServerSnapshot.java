package io.github.hhagenbuch.mcppact.core.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A point-in-time view of a provider: the tools it advertises and the
 * capabilities it declared at {@code initialize}. Compared against a {@link Pact}
 * by the contract verifier.
 */
public record ServerSnapshot(Map<String, ServerTool> tools, Set<String> capabilities) {

    public ServerSnapshot {
        tools = tools == null ? Map.of() : Map.copyOf(tools);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    /** Convenience builder from a list of tools (keyed by name) and capabilities. */
    public static ServerSnapshot of(List<ServerTool> tools, Set<String> capabilities) {
        Map<String, ServerTool> byName = new LinkedHashMap<>();
        for (ServerTool tool : tools) {
            byName.put(tool.name(), tool);
        }
        return new ServerSnapshot(byName, capabilities);
    }
}
