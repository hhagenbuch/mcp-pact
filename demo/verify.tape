# mcp-pact verify demo — reproducible terminal recording.
#
#   brew install vhs && vhs demo/verify.tape        (run from the repo root)
#
# Prerequisite: mvn -q -DskipTests package   (builds the shaded jars bin/mcp-pact runs)
#
# The scene is the whole product in four commands: an unchanged server passes, a
# renamed tool fails the build, and a description change — schema-identical, but it
# steers the model — is a WARN that stays non-fatal until you ask for --strict.
#
# Everything runs against the in-repo example server, so anyone who clones the repo
# reproduces this exactly. No network, no API key.
#
# Note: the server command is typed out in full on every line rather than held in a
# shell variable. `-- $SRV` looks tidier but zsh does not word-split an unquoted
# parameter, so the whole command arrives as a single argv entry and every verify
# fails with "failed to launch MCP server". Full commands are also what a viewer can
# actually copy.

Output docs/verify-demo.gif

Set Shell zsh
Set FontSize 15
Set Width 1300
Set Height 640
Set Framerate 10
Set TypingSpeed 11ms
Set Theme "Catppuccin Mocha"
Set Padding 14
Set Margin 8
Set WindowBar Colorful

# ---------------------------------------------------------------- setup (hidden)
Hide
Type `clear && printf '\n  \033[1;36mmcp-pact\033[0m — consumer-driven contract testing for MCP servers\n\n  \033[2mrecord what your agent actually uses · verify it in the server'"'"'s CI\033[0m\n\n'` Enter
Sleep 2s
Show
Sleep 2500ms

# ---------------------------------------------------------------- the demo
# 1. Nothing changed — the contract holds. Exit 0, green build.
Type `./bin/mcp-pact verify examples/search.mcp-pact.json -- java examples/ExampleMcpServer.java v1; echo "exit=$?"` Enter
Sleep 3500ms

# 2. The provider renamed search_code -> search. BREAKING, exit 1, red build.
#    The new tool is reported COMPAT rather than a failure: the pact only covers
#    what this consumer actually used.
Type `./bin/mcp-pact verify examples/search.mcp-pact.json -- java examples/ExampleMcpServer.java rename; echo "exit=$?"` Enter
Sleep 4500ms

# 3. Only the description changed. Every schema still holds, so this is not a build
#    failure — but the description steers the model, so it is not silence either.
Type `./bin/mcp-pact verify examples/search.mcp-pact.json -- java examples/ExampleMcpServer.java desc; echo "exit=$?"` Enter
Sleep 4s

# 4. Same drift, stricter bar: --strict promotes WARN to a failure.
Type `./bin/mcp-pact verify examples/search.mcp-pact.json --strict -- java examples/ExampleMcpServer.java desc; echo "exit=$?"` Enter
Sleep 4500ms
