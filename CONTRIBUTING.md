# Contributing to mcp-pact

Thanks for your interest. mcp-pact is a small, sharply-scoped project — a schema,
a matcher/diff engine, a verifier, a recorder, and a CI action. Contributions
that keep it that way are very welcome.

## Build & test

```bash
mvn verify
```

Java 25 and Maven. The build is hermetic: the verifier/recorder tests launch a
real stdio subprocess but pull nothing from the network.

### The end-to-end suite

Two things exercise the whole tool the way a user would:

```bash
# 1. Build the verifier CLI (what the GitHub Action runs)
mvn -q -pl mcp-pact-verifier -am -DskipTests package

# 2. Verify the in-repo example pact against the example server.
#    'v1' matches (exit 0); 'rename' is BREAKING (exit 1); 'desc' is WARN.
java -jar mcp-pact-verifier/target/mcp-pact-verifier.jar \
  verify examples/search.mcp-pact.json -- java examples/ExampleMcpServer.java v1
```

The GitHub Action self-test (`.github/workflows/self-test.yml`) does exactly
this on every push — it dogfoods `uses: hhagenbuch/mcp-pact@v1` against the
example, so a broken action fails CI before anyone depends on it.

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

## Good first issues

Good places to start, in rough order of self-containedness:

- **Taxonomy rows.** Find a tool/schema change that currently passes silently or
  is misclassified, and add its row to the `ContractVerifierTest` table with the
  right BREAKING/COMPAT/WARN verdict. Small, well-scoped, and it sharpens the
  spec. Open a **Spec / taxonomy question** issue first if the verdict is
  debatable.
- **Matcher coverage.** The core models a shallow slice of JSON Schema; extend a
  matcher (e.g. `enum`, `format`, array `items`) with focused unit tests and a
  taxonomy row for any new drift it detects.
- **Recorder robustness.** Edge cases in the stdio proxy — interleaved
  notifications, large payloads, servers that emit banner text on stderr.
- **Examples.** A new `examples/` server mode that demonstrates a drift class we
  don't yet showcase.

Issues labeled `good first issue` are curated for exactly this. If something is
unclear, a **Spec / taxonomy question** issue is always a fine place to start.

## Scope / non-goals (for now)

HTTP/SSE transport, contracts for MCP `resources`/`prompts`, and a pact broker
are intentionally out of the MVP — see [`docs/DESIGN.md`](docs/DESIGN.md). Issues
proposing these as future work are welcome; PRs are best discussed first.
