# 0138 — A read may run on OpenCode

[← decisions](README.md) · rule: [`seams`](../rules/seams.md) · amends [0137](0137-opencode-is-an-agent-cli-driver.md)

## Status

Accepted 2026-11-05.

## Context

- 0137 put the sessions on a local model; the reads (QA check, Master rounds) still run only through `claude -p`.
- A read needs tools, so it needs a CLI with a tool loop, and it must stay read-only whatever the model does.

## Decision

- `orchestrator.opencode.reads` maps a role (or `master`, the round reads) to a model; blank means `opencode.model`.
- `HeadlessOpenCode` runs `opencode run --standalone --format json` with stdin closed, its config in `OPENCODE_CONFIG_CONTENT`.
- It calls the process port directly: `AssistantProcess`'s breaker is the cloud's, and a local failure must not trip it.
- The config offers one agent `jagt-read`: tools `"*": false` first, then `read`, `grep`, `glob`, `bash`, `webfetch` where the role fetches.
- Permissions sit on `jagt-read`, deny first: `edit` deny, `external_directory` `"*"` deny then each worktree allow,
  `bash` `"*"` deny, the role's globs allow, its refused ones deny, `"*>*"` deny last.
- After the run every completed call is checked against the scope; a violation discards the answer. It is defence in depth behind the permissions.
- The answer is the last text's JSON fence, mapped through `SchemaAnswer` into the envelope the Claude readers' mappers read.
- Tokens are booked through `UsageTracker` whatever the read answered.
- `ChosenCheckerReader` and `ChosenRoundReviewer` pick per role; the per-CLI readers do not implement the port, so each consumer keeps one bean.

## Measured 2026-11-05

- An agent's `tools` needs `"*": false` first or it masks every later key.
- A custom agent ignores the top-level `permission`: under it `touch` ran; on the agent it is denied.
- The last matching bash pattern wins: `git log > f` passed `git log*` and wrote until `"*>*"` came last.
- `run --format json` prints `{type, part}` lines: `text`, `tool_use`, `step_finish` with `tokens {input, output, reasoning, cache}`.
- A denied call is a `tool_use` with status `error`, `Permission denied: shell`, and `providerCall.executed` false.
- The shell tool is `shell` in events and `bash` in config.
- Stdin left open hangs `run` to the cap.

## Rejected

- Routing through `AssistantProcess`: a local failure would trip the cloud's breaker.
- Trusting the permissions alone: the order of patterns decided a write once.

## Reopen when

- OpenCode's permissions gain a documented ordering guarantee: the after-check may go.
- A read needs files outside the worktrees: `external_directory` is unmeasured here.
