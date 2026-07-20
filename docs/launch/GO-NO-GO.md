# Launch GO / NO-GO checklist

Everything the assistant could verify is checked. The unchecked items are
**manual steps only you can/should do** — nothing here has been posted, filed, or
released.

## Verified (green)

- [x] **Fresh-clone build works.** Cloned to a clean directory and `mvn package`
      succeeds offline on **Java 25** (`openjdk 25.0.3`).
- [x] **Quickstart runs end to end** against the in-repo example:
  - `verify … v1` → `✓ no differences` , exit **0**
  - `verify … rename` → `✗ BREAKING search_code (tool.missing)` , exit **1**
  - `verify … desc` → `⚠ WARN … tool.description` , exit **0**
  - `verify … desc --strict` → same WARN, exit **1**
- [x] **Action self-test is green on `main`** (dogfoods `uses: ./` against the
      example on every push).
- [x] **Contributor kit complete** — bug / feature / **spec-question** issue
      templates, PR template (now requires a taxonomy-table row for any
      BREAKING/COMPAT/WARN change), refreshed CONTRIBUTING (dev setup, explicit
      e2e suite, taxonomy location, **good-first-issue** section).
- [x] **Staleness fixed** — CONTRIBUTING and the README badge said *Java 21*; both
      now say **Java 25** (the repo has been on Java 25 since #5).

## Manual steps before / at launch (yours)

- [ ] **Release tag decision.** The floating `v1` tag currently points at the
      original release commit, so `uses: hhagenbuch/mcp-pact@v1` resolves to
      **Java-21 code with a Java-21 action** (self-consistent and working — not
      broken — but it does *not* include the Java 25 bump or the contributor kit).
      `main` is 3+ commits ahead. Recommended: **move `v1` to `main`** (or cut
      `v1.1.0` and re-point `v1` to it) so the advertised action ships current
      code:
      ```bash
      git tag -f v1 main && git push -f origin v1     # floating major tag
      # or: git tag v1.1.0 main && git push origin v1.1.0
      ```
      Do this *after* the launch-prep PR merges, so the tag includes everything.
- [ ] **Record the 60-second GIF.** README line ~19 is still a `TODO`; a verbatim
      terminal demo block stands in for now (the `rename` → BREAKING run). Record
      an asciinema/GIF of a `verify` catching a renamed tool and replace the TODO.
      This is the one genuinely manual asset.
- [ ] **Create issue labels** before filing seeds: `enhancement`,
      `good first issue`, `spec`, `transport`, `help wanted`.
- [ ] **File the seed issues** in `docs/launch/seed-issues.md` (6 drafted). Or say
      the word and I'll file them via `gh`.
- [ ] **Post — in this order, only after the PR is merged and the tag is set:**
      1. Show HN (`show-hn.md`) — pick title option #1.
      2. MCP Discord (`mcp-discord.md`) — check channel rules first.
      3. r/mcp (`reddit-r-mcp.md`) — check the sub's self-promo rules.
      Keep `faq.md` open while you post.

## Note on this file's siblings

`show-hn.md`, `reddit-r-mcp.md`, `mcp-discord.md`, `faq.md`, and `seed-issues.md`
will be **public** once this PR merges (they live in the repo). They're written to
read fine in the open, but if you'd rather not publish the posting playbook, drop
`docs/launch/` from the PR and keep it local — say so and I'll adjust.
