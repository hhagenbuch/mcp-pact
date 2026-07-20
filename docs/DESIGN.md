# mcp-pact — Design (RFC)

**Status:** Draft / pre-code (Phase 0)
**Author:** Heyward Hagenbuch

Consumer-driven contract testing for MCP tool calls. Agents record what they
depend on; servers verify against it in CI; breaking changes become a red build.

## 1. Problem

MCP wires agents to tool servers over JSON-RPC. When a server renames a tool,
tightens a parameter, drops a field, or changes response shape, **nothing fails
at deploy time** — the agents depending on it just quietly degrade. There is no
testing story for this yet, and everyone integrating agents with MCP servers is
one `tools/list` change away from silent breakage.

`mcp-pact` ports the decade-old lesson of consumer-driven contracts (Pact) to
MCP: the *consumer* declares the interactions it relies on; the *provider*
verifies against them. The contract is recorded from real traffic, not
hand-written, so adoption is realistic.

## 2. The pact file (`*.mcp-pact.json`) — the real product

A versioned, documented JSON schema (see [`SCHEMA.md`](SCHEMA.md) and
[`../schemas/mcp-pact.schema.json`](../schemas/mcp-pact.schema.json)). Example:

```json
{
  "pactVersion": "0.1",
  "consumer": "support-agent",
  "provider": "workspace-tools-mcp",
  "expectations": [
    {
      "tool": "search_code",
      "inputSchema": { "...captured JSON Schema subset the consumer uses..." },
      "requiredCapabilities": ["tools"],
      "interactions": [
        {
          "description": "search by symbol name",
          "input": { "query": "CartService", "limit": 5 },
          "response": {
            "matchers": [
              { "path": "$.content[0].type", "equals": "text" },
              { "path": "$.content[0].text", "regex": "CartService" },
              { "path": "$.isError", "equals": false }
            ]
          }
        }
      ]
    }
  ]
}
```

### Key decision: matchers, not literal equality
MCP tool output is often nondeterministic (timestamps, ordering, generated
text). Pacts therefore assert **shape and invariants** via path matchers
(`equals`, `regex`, `type`, `present`), never full literal equality. Pact
learned this a decade ago; we inherit it deliberately.

## 3. Breaking-change taxonomy (the verifier's brain)

Provider drift is classified like semver and printed that way:

| Class        | Triggers |
|--------------|----------|
| **BREAKING** | a tool the pact uses is missing or renamed; a used input param is removed or its type changed; a **new required** input param is added; a response matcher fails on replay; a required server capability is withdrawn |
| **COMPAT**   | new optional params; new tools; a looser input schema; extra response fields not asserted on |
| **WARN**     | a used tool's **description** changed materially |

The **WARN** tier is the MCP-specific contribution: a description change is
schema-true but behavior-changing, because the description is part of the prompt
the model reasons over. Highlighted in the README.

### The load-bearing invariant: understood, or flagged — never silent

The diff models a *shallow* slice of JSON Schema precisely: top-level property
presence, required-ness, and plain string `type`s. That is a deliberate scope
cut — but a shallow diff must never *silently bless* a change it did not
understand. So anything deeper or unrecognized degrades to **WARN
`param.schemaDetails`** rather than passing:

- a `type` given as an array (`["string","null"]`) — not a plain string, so the
  precise type comparison can't apply;
- enum narrowing, `pattern`/`minLength` tightening, nested-object changes — any
  case where the shallow types agree but the property's schema subtree differs
  from the recorded one.

The rule: for a used param present on both sides, if both declare a plain string
`type` and they differ → **BREAKING `param.type`**; otherwise, if the recorded
and current property subtrees are not equal → **WARN `param.schemaDetails`**.
"We either understood the change or we flagged it" is the whole thesis in one
sentence.

### Description materiality

Whitespace-, case-, and punctuation-only description edits are ignored. A
genuine reword is compared by **token-overlap (Jaccard)**; below ~0.8 similarity
it is a material **WARN `tool.description`**, above it it's treated as a minor
edit and skipped, so `--strict` gates don't fail on trivial rewrites. A *removed*
description is always **WARN `tool.descriptionRemoved`** (a missing description
changes model behavior more than a reworded one). Known limitation: a
single-token typo fix lowers overlap and may still flag — an acceptable
false-positive given how rare that is.

### Capabilities are dotted paths

`requiredCapabilities` entries are matched against the provider's negotiated
capabilities flattened to dotted paths, so a pact can require either a top-level
capability (`"tools"`) or a nested one (`"resources.subscribe"`) with no format
change — future-proofed now to avoid a later `pactVersion` bump.

### Exit codes

`0` = the contract holds. `1` = a BREAKING difference (or a WARN under
`--strict`). `2` = the check could not be run: a usage error, an **invalid pact**
(bad path/regex/type), or the provider failed to launch or respond. The 1-vs-2
split is deliberate — "the contract is broken" must be distinguishable from "we
couldn't check it". COMPAT is always informational.

## 4. Components (one Maven multi-module repo, Java 21)

1. **`mcp-pact-core`** — pact model, JSON (de)serialization, schema-diff engine,
   matcher engine. **Zero MCP dependency**; pure logic, exhaustively unit
   tested. The taxonomy test suite (§7 Phase 1) *is* the spec.
2. **`mcp-pact-recorder`** — a transparent stdio proxy: `agent ↔ recorder ↔
   real server`. JSON-RPC 2.0 passthrough that observes `initialize`,
   `tools/list`, `tools/call`, accumulates expectations, and writes the pact on
   exit. Usage: `mcp-pact record --out support-agent.mcp-pact.json -- npx some-mcp-server`.
3. **`mcp-pact-verifier`** — shaded-jar CLI: `mcp-pact verify pact.json --
   <server command>`. Launches the server over stdio, runs the `tools/list` diff
   + interaction replay, prints a BREAKING/COMPAT/WARN report (human + `--json`),
   and sets the exit code.

   **Transport decision (revised in Phase 2):** the MVP uses a self-contained
   stdio JSON-RPC client (`StdioMcpConnection`) behind an `McpConnection`
   interface rather than the official MCP Java SDK. Rationale: hermetic tests
   (a real subprocess, no network or `npx` pulls at build time) and reuse of the
   proven newline-delimited JSON-RPC approach. Because the diff/replay logic
   depends only on `McpConnection`, adopting the official
   `io.modelcontextprotocol.sdk` client later is an interface-level swap — kept
   as a roadmap item, with the MCP spec revision it targets noted then.
4. **`examples/`** — a tiny in-repo MCP server (Java) + consumer pact wired into
   CI as a self-test, plus a recorded pact against a public reference server
   (e.g. the filesystem server) as a realism proof.
5. **GitHub Action** (`action.yml`) — providers add "verify pacts on every PR"
   in ~5 lines. This is the growth loop: a provider CI badge that says "consumer
   contracts verified."

## 5. Consumer-exercised schema capture (the subtle bit)

The recorder captures **the subset of the input schema the consumer actually
used** — the fields it sent — not the server's full advertised schema. That is
what "consumer-driven" means: the contract encodes the consumer's real
dependency surface, so the verifier flags a breaking change only when it breaks
*this* consumer. Belongs in the README's design section.

## 6. Non-goals (MVP)

- HTTP/SSE transport (stdio only for MVP; HTTP is a roadmap item). When we take this on,
  the SSE stream-resumption behavior is a spec input to get right — see MCP
  [#1939, "Define behavior when Last-Event-ID is unresumable"](https://github.com/modelcontextprotocol/modelcontextprotocol/issues/1939)
  (an unresumable `Last-Event-ID` is a distinct case the recorder/verifier must handle, not
  a JSON-RPC message-id concern).
- Contracts for MCP `resources` / `prompts` (tools only for now).
- A pact broker / central registry (pacts live in repos for MVP).

## 7. Build phases

0. **Design** — this doc + the JSON schema, committed first, alone.
1. **`mcp-pact-core`** — model + matcher engine + schema-diff with the full
   BREAKING/COMPAT/WARN taxonomy as **table-driven** unit tests (~30 cases; make
   it exhaustive — this suite is the spec). No I/O.
2. **`mcp-pact-verifier`** — MCP Java SDK stdio client, `tools/list` diff,
   interaction replay, report formatting, exit codes. e2e against the in-repo
   example server plus a mutated "v2" that triggers each BREAKING class.
3. **`mcp-pact-recorder`** — JSON-RPC passthrough proxy with observation and
   consumer-exercised schema capture. Roundtrip e2e: record → verify passes →
   mutate server → verify fails correctly.
4. **Action + README + launch** — `action.yml`, CI self-test badge, README with
   a 60-second GIF of a verify run catching a renamed tool, taxonomy table,
   consumer+provider quickstarts, `CONTRIBUTING.md`.

## 8. Guardrails

Clean room. No employer references anywhere. No resemblance to any internal MCP
work beyond the public MCP spec.
