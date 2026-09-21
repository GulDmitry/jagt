# The protocol

[← AGENTS.md](../../AGENTS.md)

**Everything that crosses into jagt from outside is a message, and a message is validated once, at the door.**
`protocol/` in `core/` holds them: the wire record, its rules, and the types the rest of jagt works in. It
speaks the centre's vocabulary and nothing else, and the centre does not know it exists (`RingsTest`).

## No raw wire value travels inward

- A message arrives as the strings and maps it was written in. `violations(...)` judges it; `accepted(...)`
  returns it in jagt's own types (`protocol/Reported`) or nothing.
- **Nothing downstream re-reads the wire.** Which status, what the round claims, which request it is about and
  what the human is shown are resolved in `protocol/`, once. A caller that parses a field again has found a
  leak, not a convenience.
- What a message cannot carry is not the protocol's to answer: a claim it makes about the worktree is measured
  where the worktree is (`service/HandBack`), and which status it may LAND on is `flow/FlowRules`.

## Two kinds of rule, one report

- **Field**: a value out of its enum, a missing required field, a link nobody can open.
- **Consistency**: two fields that cannot both hold — `reviewRequests` beside `reviewRequestUrl`, CI_POLLING
  with no request anywhere in the message, a request filed under a project the task does not have.
- **Every violation at once**, never first-failure: the sender is usually a model, and one error per call is
  one call per error. `startup/StartupValidation` refuses a bad install the same way.
- A violation names **the field and what was expected**, because the refusal is what the sender acts on.

## Where the shapes are today

`protocol/AgentStatusMessage` is the first message gathered. The others are still scattered — the MCP tool
declarations are inline schema strings in `surface/mcp/tools/`, and the paid reads carry `--json-schema` blocks
in `adapter/assistant/`. Gathering them is [`TODO.md`](../../TODO.md); a schema expresses the field rules and
never the consistency ones, so both halves belong wherever they land.
