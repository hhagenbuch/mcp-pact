package io.github.hhagenbuch.mcppact.verifier;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.hhagenbuch.mcppact.core.model.ServerSnapshot;

/**
 * A live connection to an MCP provider, used by the verifier to discover tools
 * and replay interactions. Transport is behind this interface so the MVP's
 * self-contained {@link StdioMcpConnection} can later be swapped for the
 * official MCP Java SDK without touching the verification logic.
 */
public interface McpConnection extends AutoCloseable {

    /** The provider's advertised tools and capabilities (post-handshake). */
    ServerSnapshot listTools();

    /** Invokes {@code tools/call} and returns the raw {@code result} node. */
    JsonNode call(String tool, JsonNode arguments);

    @Override
    void close();
}
