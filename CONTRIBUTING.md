# Contributing to mcp-pact

Thanks for your interest. mcp-pact is a small, sharply-scoped project — a schema,
a matcher/diff engine, a verifier, a recorder, and a CI action. Contributions
that keep it that way are very welcome.

## Build & test

```bash
mvn verify
```

Java 21 and Maven. The build is hermetic: the verifier/recorder tests launch a
real stdio subprocess but pull nothing from the network.

## Layout

- `mcp-pact-core` — pact model, matcher engine, and the BREAKING/COMPAT/WARN
  schema diff. **No I/O.** The taxonomy lives in `ContractVerifierTest` as a
  table — that suite *is* the spec.
- `mcp-pact-verifier` — the `verify` CLI: stdio client, `tools/list` diff, and
  interaction replay.
- `mcp-pact-recorder` — the `record` CLI: a transparent stdio proxy that writes
  a pact from real traffic.
- `action.yml` + `examples/` — the GitHub Action, dogfooded by the self-test.

## Guiding principle

**Understood, or flagged — never silent.** The diff models a shallow slice of
JSON Schema precisely; anything deeper or unrecognized must degrade to a WARN,
not pass. If you add a diff rule, add its row to the taxonomy table test.

## Pull requests

- Branch off `main`; keep one concern per PR.
- Add or extend tests — for a new diff rule, a taxonomy row; for a matcher or
  recorder change, a focused unit test.
- Run `mvn verify` and make sure CI (build + action self-test) is green.
- Describe the before/after behavior in the PR body.

## Scope / non-goals (for now)

HTTP/SSE transport, contracts for MCP `resources`/`prompts`, and a pact broker
are intentionally out of the MVP — see [`docs/DESIGN.md`](docs/DESIGN.md). Issues
proposing these as future work are welcome; PRs are best discussed first.
