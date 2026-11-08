## Why

A stop asks its human who looped, which defect family it is and whether a lift is safe, and each answer is a read of the
trail and the commits. A read-only role can give the three on the stop's hover, so the human decides faster and nothing else moves.

## What Changes

- A stop is read once by the `triage` role when `roles.triage.enabled` is set; the answer is one hover line.
- A stopped task still waits for the human's lift: triage presses nothing, notifies nobody, writes no git or code host.
- Decision: [0149](../../../docs/decisions/0149-triage-is-a-stop-read-not-a-checker.md).

## Impact

- Spec: `task-stops`.
- `roles/triage/` (brief and eval), `StopTriage` on `TaskStop`, `StopTriagePanel`, `StopTriageJob`, the hover line, `roles.triage.enabled` and `model`.
