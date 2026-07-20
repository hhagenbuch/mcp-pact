# Seed issues (drafts for review — file after launch)

Six issues drawn from the real roadmap, written up so the work that's actually
open is legible to a newcomer instead of living only in maintainers' heads.
`good first issue` is applied only where it's honestly small and
self-contained. **Nothing here has been filed** — file them yourself.

This file is a staging area, not documentation: once these are filed as real
issues, the tracker is the source of truth and this file gets deleted.

Suggested labels to create first: `enhancement`, `good first issue`, `spec`,
`transport`, `help wanted`.

---

## 1. HTTP/SSE transport for `verify` and `record`

**Labels:** `enhancement`, `transport`, `help wanted`

Today the verifier and recorder speak MCP over **stdio** only (they launch the
server as a subprocess and frame JSON-RPC over stdin/stdout). A growing number of
MCP servers are reachable over the **Streamable HTTP / SSE** transport instead,
and those can't be contract-tested yet.

Scope:
- A transport abstraction behind the current `StdioMcpConnection` so `verify` and
  `record` can target either `-- <command>` (stdio, today) or `--url <http…>`.
- Honor the MCP HTTP transport handshake (session id header, SSE event stream).
- The pact format itself shouldn't need to change — a pact is transport-agnostic;
  only the connection does.

This is the single most-requested capability for real-world adoption. Non-trivial
(a new connection type + tests), so not a first issue, but well-bounded.

---

## 2. A minimal pact broker (publish / fetch shared pacts)

**Labels:** `enhancement`, `help wanted`

Right now a pact file lives in the consumer's repo and is copied to the provider
to verify. That works for one consumer; it doesn't scale to "which of my twelve
agent consumers still depend on this tool?" A lightweight broker — publish a pact
under `(consumer, provider)`, fetch all pacts for a provider — would let a server's
CI verify against *every* recorded consumer at once.

Scope for a first cut:
- `mcp-pact publish` / `mcp-pact fetch` against a pluggable store (start with a
  git-backed or S3-backed directory; no service to run).
- Provider CI: fetch all pacts for `provider=X`, verify each, aggregate the exit
  code.

Deliberately out of the MVP (see `docs/DESIGN.md`); this issue tracks the design
discussion before any code.

---

## 3. Contracts for MCP `resources` and `prompts`, not just `tools`

**Labels:** `enhancement`, `spec`

A pact currently captures the `tools` surface (`tools/list` + `tools/call`
interactions). MCP servers also expose `resources` and `prompts`, and those drift
too — a renamed resource URI or a changed prompt argument breaks consumers just as
silently as a renamed tool.

Scope:
- Extend the pact schema with optional `resources` / `prompts` sections.
- Add `resources/list` and `prompts/list` diffs with their own taxonomy rows
  (what's BREAKING vs COMPAT vs WARN for each).
- Recorder captures them when the consumer uses them.

Best discussed as a **spec/taxonomy** question first — the classification rules
are the interesting part. Medium size.

---

## 4. `--max-warn N` threshold flag

**Labels:** `enhancement`, `good first issue`

`verify` exits non-zero on any BREAKING; `--strict` also fails on any WARN. There's
no middle setting. Some teams want "WARNs are fine up to a point" — fail only when
there are more than N.

Scope (small, self-contained):
- Add `--max-warn N` to the verifier CLI.
- Exit non-zero when `warnCount > N` (independent of `--strict`; document how they
  interact — `--strict` is equivalent to `--max-warn 0`).
- One CLI test per branch (under, at, over the threshold).

Good first issue: the diff already counts warns; this is plumbing a flag to the
exit-code decision.

---

## 5. Extend matcher coverage: `enum`, `format`, array `items`

**Labels:** `enhancement`, `good first issue`

The core models a shallow slice of JSON Schema precisely and WARNs on anything
deeper. A few common keywords are worth modelling exactly so their drift is
classified instead of warned:
- `enum` — removing a value is BREAKING, adding one is COMPAT.
- `format` — tightening (e.g. adding `format: uri`) is BREAKING for inputs.
- array `items` — a changed element schema.

Scope: one matcher + its **taxonomy table rows** per keyword. Each is independently
shippable — take one keyword, add the row(s), done. Ideal first contribution.

---

## 6. Machine-readable output for CI annotations

**Labels:** `enhancement`, `good first issue`

`--json` exists for the full result; add a compact mode that emits **GitHub
Actions annotations** (`::error file=…::` / `::warning::`) so a failing verify
shows up inline on the PR diff, not just in the log.

Scope (small):
- `--format github` (or detect `GITHUB_ACTIONS=true`) prints one annotation line
  per finding, mapped by drift class (BREAKING→error, WARN→warning).
- The human summary still prints to stdout.

Good first issue: it's a second formatter over the existing result object.
