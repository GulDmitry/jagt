# 0105 — Infrastructure failures are waited out

[← decisions](README.md) · rule: [`infra-wait`](../../openspec/specs/infra-wait/spec.md)

## Status

Accepted 2026-10-29. Supersedes in part [0011](0011-a-red-run-is-the-sessions-to-diagnose.md): a red that is not code no longer reaches the session.

## Context

- Two tasks (ABC-42, ABC-43) went red on a lost runner. The session was told to fix a build that was not broken and asked a human about it.
- 0011 named this: "Sessions answer most red runs with a question about infrastructure: a retry verb then pays for itself."
- The host already says why a job failed (`failure_reason`); the sweep's MCP tools do not return it, the host's CLI does.

## Decision

- jagt re-runs a pipeline the host says died on infrastructure, bounded by `infraWait.backoff` (default 5m, 15m, 45m); `[]` turns it off.
- It is jagt's one code-host write, so AGENTS.md names it as the exception to "nothing else acts unasked".
- The reasons are read by the adapter (`PipelineRetry`, the host's CLI), never by the sweep's model: the verdict is deterministic.
- Infrastructure: `runner_system_failure`, `stuck_or_timeout_failure`, `api_failure`, `scheduler_failure`, `data_integrity_failure`;
  for a run `startup_failure`. Not `unknown_failure`; any other reason, a mix, or none is the code's.
- Every infrastructure red keeps the task's status and tells the session nothing; open threads are still relayed, without the checks.
- After the last wait, or a refused re-run, the task stops as `INFRA` with the CLI's own words.
- Failed jobs that cannot be listed leave the red the code's.
- The retry and the stop apply only where the task waits on its checks (`FlowRules.waitsOnTheHost`); past the review a red run is nobody's wait.
- The re-run is `ActionOrigin.JAGT`, an unattended move that counts toward `loopBreaker.perHour`.

## Rejected

- A retry verb the session presses: the session cannot know the host's reason either.
- Reading `failure_reason` through the sweep's MCP: `get_job` does not return it.
- Treating `unknown_failure` as infrastructure: it is also what a crashing script leaves, so a code failure would wait.
- Reusing `AUTO_REVIEW` for the re-run: that origin only reads and drafts.

## Reopen when

- A retry lands green on a pipeline the host called infrastructure for the third time in a row: 3 waits are too many.
- The host adds a reason that is infrastructure and is missing here, or a reason here that was the code's.
