# r/mcp (or r/modelcontextprotocol) draft (do not post — for review)

**Where:** the MCP subreddit. Check the sub's self-promotion rules first — many
require a text post (not a bare link) and some gate tool posts to a weekly thread.
**Register:** text post, explain the problem, link at the end.

---

**Title:** Contract testing for MCP servers — catch breaking tool changes in CI
before your agents do

**Body:**

There's no compile step between an MCP server and the agents that consume it. So
when a server renames a tool, tightens an input schema, or changes a response
shape, nothing fails at deploy — the consuming agents just quietly get worse. That
failure mode ("the agent got dumber") is miserable to debug because nothing
errored.

I built **mcp-pact** to close that gap using consumer-driven contracts (the Pact
idea, applied to MCP):

- **Record:** run your agent through a transparent stdio proxy; it captures the
  tools, input schemas, and response shapes the consumer actually used into a
  pact file.
- **Verify:** in the server's CI, replay the pact and classify any drift as
  **BREAKING / COMPAT / WARN**. Renamed/removed tool → BREAKING (red build). New
  tool → COMPAT (fine). Changed tool *description* → WARN (schema's fine, but the
  description steers the model, so a human should look).

In CI it's a GitHub Action, a few lines of YAML. It's MVP and stdio-only right now
(HTTP/SSE transport is the top roadmap item), Java under the hood.

The design doc and the full taxonomy (it's a table in one test file — that suite
*is* the spec) are in the repo. I'd especially like feedback on where the
BREAKING/COMPAT/WARN lines should be drawn — that's the part that gets better with
other people's real-world drift stories.

Repo: https://github.com/hhagenbuch/mcp-pact
