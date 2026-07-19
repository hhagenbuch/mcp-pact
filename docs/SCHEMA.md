# The `*.mcp-pact.json` format

Version `0.1`. Normative JSON Schema:
[`../schemas/mcp-pact.schema.json`](../schemas/mcp-pact.schema.json). This page
explains it; the schema file is the source of truth.

## Top level

| Field         | Type   | Req | Meaning |
|---------------|--------|-----|---------|
| `pactVersion` | string | ✓   | Pact format version (`"0.1"`). |
| `consumer`    | string | ✓   | Name of the agent/consumer that recorded this pact. |
| `provider`    | string | ✓   | Name of the MCP server the pact is against. |
| `expectations`| array  | ✓   | One entry per tool the consumer depends on. |

## Expectation (per tool)

| Field                  | Type   | Req | Meaning |
|------------------------|--------|-----|---------|
| `tool`                 | string | ✓   | Tool name as used via `tools/call`. |
| `description`          | string |     | The tool description observed at record time. A material change to it on the provider surfaces as **WARN** (schema-true, behavior-changing). |
| `inputSchema`          | object | ✓   | **Consumer-exercised** subset of the tool's input JSON Schema — only the fields the consumer actually sends. |
| `requiredCapabilities` | array  |     | MCP capabilities the consumer needs (e.g. `["tools"]`). |
| `interactions`         | array  | ✓   | Concrete recorded calls and their expected response shape. |

## Interaction

| Field         | Type   | Req | Meaning |
|---------------|--------|-----|---------|
| `description` | string | ✓   | Human label for the scenario. |
| `input`       | object | ✓   | The exact arguments sent in `tools/call`. |
| `response`    | object | ✓   | `{ "matchers": [ ... ] }` — see below. |

## Matchers

Responses are validated by **matchers on JSON paths**, never literal equality
(MCP output is often nondeterministic). A matcher is `{ "path": "<JSONPath>",
<predicate> }` where the predicate is exactly one of:

| Predicate           | Passes when |
|---------------------|-------------|
| `"equals": <value>` | value at `path` deep-equals `<value>`. |
| `"regex": "<re>"`   | string value at `path` matches the regex. |
| `"type": "<t>"`     | value at `path` has JSON type `<t>` (`string`/`number`/`boolean`/`object`/`array`/`null`). |
| `"present": true`   | `path` exists (any value). |

Paths use a small JSONPath subset: `$`, dot fields (`$.isError`), and array
indexes (`$.content[0].text`). A matcher whose `path` does not resolve fails
(and, on a used field, classifies as **BREAKING**).

## Example

See the example in [`DESIGN.md`](DESIGN.md) §2.
