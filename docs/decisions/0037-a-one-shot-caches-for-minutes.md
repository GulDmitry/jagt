# 0037 — A one-shot caches for minutes

[← decisions](README.md) · rule: [`seams`](../rules/seams.md#every-assistant-call-is-metered)

## Status

Accepted 2026-10-13.

## Context

Task transcripts 2026-10-02..13: cache writes cost more than reads, 37M written at 2× against 483M read at 0.1×.
Headless first calls wrote 26.3M of the 37M, all at the 1h TTL, a median 45k each. Of 739 headless runs, 21 read
more than the common prefix from an earlier run, one of them more than 5 minutes later. A run lasts 0.5 minutes,
1.6 at p90.

## Decision

- Every `HeadlessClaude` run, the Master's included, runs the CLI with `FORCE_PROMPT_CACHING_5M=1`: a 5m write costs 1.25×, a 1h write 2×.
- Interactive sessions keep the 1h TTL: their next turn may come after five minutes.

## Rejected

- The 4-argument `ProcessRunner.run` with an environment map: 49 test stubs would change for one variable.

## Reopen when

- `stats` shows headless COLD starts rising: the shared prefix expiring between runs costs more than it saves.
- A panel's first role outruns the 5-minute window, so the later roles find the round expired ([0038](0038-a-panels-first-role-writes-the-round.md)).
