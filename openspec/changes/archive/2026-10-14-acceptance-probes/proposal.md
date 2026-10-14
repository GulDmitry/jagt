## Why

A plan's acceptance checks were the session's claim: the agent ran them, or said it had. A gate is deterministic only
where it reads facts the agent cannot author.

## What Changes

- `plan.md` may declare acceptance probes in a fenced `probes` block: `command`, `ref` or `artifact`.
- jagt reads them as the plan is put up; a line it cannot read refuses the report.
- At VERIFYING jagt runs them itself; a red one returns the round with its result, no model asked.
- The Master reads each result as a fact.
- `ToolGate` moves to `service/`, judging a bare line for a session and a probe alike.

## Capabilities

### New Capabilities

- `acceptance-probes`: checks a ratified plan declares and jagt runs.
