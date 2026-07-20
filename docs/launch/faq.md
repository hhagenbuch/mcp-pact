# Prepared FAQ (for the poster — answer in your own words)

Short, concrete, non-defensive answers to the predictable questions. Don't
copy-paste these verbatim; they're the substance to draw on.

---

### "Why not just diff the JSON Schema of `tools/list`?"

That's a big part of what it does — but a raw schema diff answers the wrong
question. It tells you *what* changed, not *whether it matters to a consumer*, and
it has no notion of severity. mcp-pact adds two things a diff doesn't:

1. **Consumer scope.** A pact records only the tools and fields *this consumer
   actually used*. A server can add ten tools and change five your agent never
   touches — a schema diff lights up red; mcp-pact stays green because none of it
   affects you. Consumer-driven is the whole point.
2. **A severity taxonomy.** Removing an enum value is BREAKING; adding one is
   COMPAT; a description change is WARN. A plain diff can't make those calls, so it
   either cries wolf on everything or makes you eyeball each change. The taxonomy
   is the product; the diff is a substrate.

### "Why is a description change only a WARN? Isn't that a real break?"

It might be — that's exactly why it's a WARN and not a pass. The tool's *contract*
(name, input schema, output shape) is unchanged, so nothing will error. But the
description is an input to the model's tool-selection, so a materially different
description can change behavior without changing a single schema. There's no
principled way to decide from the outside whether that behavior change is fine, so
mcp-pact refuses to guess in either direction: it doesn't fail the build (the
contract holds) and it doesn't stay silent (behavior may have shifted). It says "a
human should look." `--strict` promotes WARN to a failure for teams that want the
stricter bar.

### "Why Java?"

Two honest reasons. First, the core (schema model, matcher, diff taxonomy) is pure
logic with heavy table-driven tests, and it's the same JVM most of my agent
tooling runs on, so it slots in. Second, MCP's transport is just framed JSON-RPC —
the language is irrelevant to the contract. The pact file is plain JSON and the
CLI is a single jar; nothing about consuming it is Java-specific. If a Node or
Python reimplementation of the verifier read the same pact format, that'd be a
good outcome, not a competitor.

### "How is this different from Pact (pact.io) itself?"

Same idea, different protocol. Pact does consumer-driven contracts for HTTP/message
APIs; its matchers and broker assume request/response over those transports. MCP
has its own surface — `tools/list`, tool input schemas, `tools/call` results, and
the fact that a *description* is behavior-affecting — none of which Pact models.
mcp-pact is small and MCP-native rather than a Pact plugin: the pact file is
MCP-shaped and the taxonomy encodes MCP-specific severity (the WARN-on-description
rule has no Pact equivalent). It borrows the philosophy, not the code.

### "What about HTTP transport / Streamable HTTP servers?"

Stdio-only today — the verifier and recorder launch the server as a subprocess and
frame JSON-RPC over stdin/stdout. HTTP/SSE is the **top roadmap item** (there's an
open issue) and it's well-bounded: the pact format is transport-agnostic, so it's a
new connection type behind the same interface, not a redesign. If HTTP support is
what would make you adopt it, say so on the issue — it moves the priority.

### "Does recording capture secrets?"

It captures the tool interactions the consumer makes — tool names, the input
arguments sent, and the response shapes. If your agent passes a secret *as a tool
argument*, that value would land in the pact, same as any request log. Two things
to know: (1) a pact is meant to be committed and reviewed like a test fixture, so
review it before committing, and (2) redaction of argument values is a reasonable
feature request if you have a concrete case — open an issue. Today, treat a pact
file with the same care as a recorded HTTP fixture.

### "Is it production-ready?"

It's honestly labeled MVP. The record → verify → CI loop works end to end, the
GitHub Action dogfoods itself on every push, and the taxonomy is tested. What's not
there yet: HTTP transport, a pact broker, and contracts for `resources`/`prompts`
(tools only today). The roadmap issues lay out exactly what's missing — nothing is
hand-waved as done that isn't.
