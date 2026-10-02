# 0008 — A failed read is never "not found"

[← decisions](README.md) · rule: [`seams.md`](../rules/seams.md#a-read-that-failed-is-never-an-answer)

## Status

Accepted 2026-08-24 (`b5a7c19`), recorded retroactively 2026-10-02.

## Context

On 2026-08-24 a `resume` refused a live merge request with "could not read (or not found)" and logged nothing.
The code host's MCP server had never started: the docker daemon was not running. The prompt told the model to
answer `exists=false` when it could not read, so "no such tool in this session" arrived as the same valid JSON as
a deleted request, and no layer below treated it as a failure.

## Decision

- "I could not look" and "there is no such thing" are two answers, never merged in a surface, a log line or a
  prompt.
- Every model-backed read carries a `failure` field, empty only when the host itself answered.
- A stated failure returns no facts at all, logged at ERROR, never `exists=false`.
- On an unreadable read jagt asks the CLI which MCP servers are down (`McpHealthProbe`, `claude mcp list`, cached
  two minutes) and logs one of three answers: down / none down / not established.
- `resume`, `do` and the review sweep each say which of the two happened, in those words.

## Rejected

- Warning "gone or unreachable" on every `exists=false` (`2c74c5c`, 34 minutes earlier): it named the doubt
  but still answered two cases with one, and guessed an interactive login as the cause.
- A two-valued probe: a probe that could not run would print "nothing is down" and clear the failure it explains.

## Reopen when

- A read with an empty `failure` reports `exists=false` for a thing that exists: the model's own word is not
  enough, and the host must answer the absence itself.
