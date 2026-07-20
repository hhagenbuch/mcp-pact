# Show HN draft (do not post — for review)

**Where:** news.ycombinator.com/submit · **Type:** Show HN, link to the repo
(https://github.com/hhagenbuch/mcp-pact), body as the first comment.

## Title options (pick one)

1. `Show HN: mcp-pact – contract testing for MCP servers, so tool drift fails CI`
2. `Show HN: Catch breaking MCP server changes in CI before your agents do`
3. `Show HN: mcp-pact – consumer-driven contract tests for Model Context Protocol`

(#1 is the most concrete and self-explaining — recommended.)

## Body (first comment)

When an MCP server renames a tool, tightens a parameter, or changes a response
shape, nothing fails at deploy time. The agents depending on that server just
quietly get worse — a tool call the model used to make now errors, or worse,
silently returns a different shape and the model carries on. There's no compile
step between an MCP server and its consumers, so the failure surfaces in
production as "the agent got dumber," which is about the hardest thing to debug.

mcp-pact borrows the consumer-driven contract idea from Pact and points it at MCP:

1. **Record** — run your agent through a transparent stdio proxy. It watches the
   `initialize` / `tools/list` / `tools/call` traffic and writes a pact file
   capturing exactly the tools, input schemas, and response shapes the consumer
   actually used. (Not the whole server surface — just what this consumer relies
   on.)
2. **Verify** — in the *server's* CI, launch it and replay the pact: diff
   `tools/list`, replay each interaction, and classify any drift.

The classification is the part I care most about getting right. Every change is
BREAKING, COMPAT, or WARN:

```
$ mcp-pact verify support-agent.mcp-pact.json -- java ExampleMcpServer.java rename
  ✗ BREAKING search_code (tool.missing): tool used by the pact is not advertised by the server (missing or renamed)
  + COMPAT * (tool.new): server advertises 1 tool(s) not covered by the pact: search
  ── 1 breaking, 0 warn, 1 compat
$ echo $?
1
```

A renamed tool is BREAKING (exit 1, red build). A *new* tool the pact doesn't
cover is COMPAT (fine). And a tool whose **description** changed — schema-identical
but behavior-affecting, since the description is an input to the model — is a WARN:

```
$ mcp-pact verify support-agent.mcp-pact.json -- java ExampleMcpServer.java desc
  ⚠ WARN search_code (tool.description): tool description changed materially since the pact was recorded — schema-true but behavior-changing
  ── 0 breaking, 1 warn, 0 compat
```

That WARN tier is deliberate: the tool didn't break its contract in any schema
sense, but it might have changed how the model uses it, and I'd rather surface
"a human should look" than either fail the build or pass silently. The whole
taxonomy lives as a table in one test file — that suite *is* the spec.

In CI it's a few lines:

```yaml
- uses: hhagenbuch/mcp-pact@v1
  with:
    pact: support-agent.mcp-pact.json
    server-command: npx @you/your-mcp-server
```

It's stdio-only today (HTTP/SSE transport is the top roadmap item), Java under the
hood, and it models a shallow-but-precise slice of JSON Schema — anything deeper
degrades to WARN rather than guessing. It's MVP but the record → verify → CI loop
works end to end and the action dogfoods itself on every push.

Design doc, schema, and the taxonomy table are all in the repo. I'd genuinely
like to hear where the BREAKING/COMPAT/WARN lines are drawn wrong — that's the
part that benefits most from other people's scars.

## Notes for the poster
- Post Tue–Thu morning US Eastern for best visibility; be around for the first
  couple hours to answer.
- If asked "why not just diff the JSON Schema?" or "why Java?" — answers are in
  `faq.md`, keep them short and non-defensive.
- Don't argue taxonomy calls in the thread; invite a `spec` issue instead.
