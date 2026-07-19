# MCP Discord draft (do not post — for review)

**Where:** the official MCP community Discord, a `#show-and-tell` / `#projects` /
`#tools` channel (check the channel rules first; some require a maintainer's OK).
**Register:** short, peer-to-peer, no marketing.

---

Hi all — I built **mcp-pact**, consumer-driven contract testing for MCP servers,
and wanted to share it with the people most likely to poke holes in it.

The problem it targets: when a server renames a tool, tightens a param, or changes
a response shape, nothing fails until an agent depending on it quietly breaks in
prod. mcp-pact records the tool interactions a consumer actually uses into a pact
file, and the server verifies against those pacts in CI. Drift is classified
**BREAKING / COMPAT / WARN** — a renamed tool fails the build; a changed tool
*description* (schema-fine but it steers the model) is a WARN, not a silent pass.

CI is a few lines:
```yaml
- uses: hhagenbuch/mcp-pact@v1
  with:
    pact: my-agent.mcp-pact.json
    server-command: npx @you/your-mcp-server
```

Repo: https://github.com/hhagenbuch/mcp-pact

It's MVP and stdio-only for now (HTTP/SSE transport is the next big item). The bit
I'd most love feedback on is the **taxonomy** — where BREAKING/COMPAT/WARN should
be drawn. If you've been bitten by MCP drift, I want to hear the shape of it.
There's a "spec / taxonomy question" issue template for exactly that discussion.
