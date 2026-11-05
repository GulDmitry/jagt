# 0137 — OpenCode is an agent CLI driver

[← decisions](README.md) · rule: [`seams`](../rules/seams.md) · stands under [0128](0128-cloud-and-local-models-alike.md)
and [0130](0130-every-third-party-is-a-configured-seam.md)

## Status

Accepted 2026-11-05.

## Context

- 0128: the model is the agent CLI's setting, jagt has no client of its own; a local model needs a CLI with a tool loop.
- OpenCode drives any OpenAI-compatible endpoint and speaks remote MCP with custom headers, the one thing the caller header needs.

## Decision

- OpenCode is a third `orchestrator.agent.cli` value, `opencode`; `OpenCodeAgentRuntime` is its driver.
- The model stays the CLI's setting: `orchestrator.opencode.endpoint` and `model` write a `local` provider into the
  session's config; blank, the user's own OpenCode config picks it.
- jagt is a remote MCP server with `X-Working-Directory`: no stdio bridge, no Node.
- The config is `.jagt/opencode/opencode.json`, reached through `OPENCODE_CONFIG`, with `OPENCODE_DISABLE_PROJECT_CONFIG=1`:
  a repository's own `opencode.json` is neither read nor written.
- Permissions are `allow`: nobody approves in the window. OpenCode has no sandbox, so startup refuses it with
  `agent.sandbox.enabled`.
- Plan mode is `OPENCODE_CONFIG_CONTENT='{"default_agent":"plan"}'` on the launch line.
- Startup refuses an endpoint that is no http(s) address and an endpoint without a model.

## Measured 2026-11-05

- Remote MCP with a registered worktree header: `mcp connected tools=4`; an unknown caller's 401 reads `needs_auth`.
- `opencode mcp list` shows the shared background service's cached status, not the session's.
- `run --standalone --format json` on Qwen3.5-4B Q6 via llama-server answered master eval case 01 correctly in 24 s
  with one `git diff` call, the answer in a ```json fence.
- An agent's `tools` needs `"*": false` first or it masks later keys: 40 `skill` calls and a wrong verdict.
- Two `--standalone` TUIs run side by side.
- Stdin left open hangs `run`: `</dev/null` is needed.
- Plan mode: the top-level CLI has no `--agent` flag. With `OPENCODE_CONFIG` set, the status line reads `Build ·`;
  adding `OPENCODE_CONFIG_CONTENT='{"default_agent":"plan"}'` reads `Plan ·`, so it merges over the file.

## Rejected

- A stdio bridge as for Codex: remote MCP with the header connects.
- A model client of jagt's own: 0128.
- The project's own `opencode.json` as the carrier: a tracked file is not ours to write.

## Reopen when

- OpenCode ships a sandbox: the refusal goes.
- A `--agent` flag reaches the top-level command: plan mode needs no environment variable.
