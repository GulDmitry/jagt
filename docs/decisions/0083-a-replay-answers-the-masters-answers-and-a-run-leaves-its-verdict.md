# 0083 — A replay answers the Master's answers, and a run leaves its verdict

[← decisions](README.md) · rule: [`testing`](../rules/testing.md#the-e2e-matrix-and-the-model-evals) · amends:
[0078](0078-a-stand-run-replays-without-a-model.md) (its Reopen-when on Master answers)

## Status

Accepted 2026-10-25.

## Context

0078 left a Master answer to a session's question unrecorded, so a replay reaching one diverged. A live run's verdict
was lost: `build/stand/report.txt` is overwritten by the next run, a `-Pmodelfree` one included.

## Decision

- A Master answer is recorded from `master-decisions.md` in the worktree, which `MasterVerdicts` appends to as
  `- asked: Q — decided: D`; the backend log clips both to 200 characters, the file does not.
- A replay answers a read opened by `RoundReviewer.QUESTION_LEAD` with the next recorded decision as one finding,
  and ends as a divergence naming `master` when none is left.
- The decision is replayed flattened to one line without its `newTask`: a replay compares steps, and the relayed
  text is not one; `newTask` only adds a "Not opened" note to that text.
- Every run writes `build/stand/runs/<time>-<scenario>-<root>.json`: scenario, mode, live or replay, passed, each
  violation, wall seconds, spend per kind or why it is unknown. The last 20 runs stay.
- `build/stand/recordings/` keeps the recordings of the last 20 runs, a run being the pid in the file name.

## Rejected

- Reading the answer from the log: clipped, so a replayed decision would differ from the one the Master gave.
- Appending to `report.txt`: it is reset on every `standTest`, which is the loss this fixes.

## Reopen when

- A replay's outcome depends on a `newTask` or on the decision's line breaks.
- Twenty runs no longer cover the days between two checks of the record.
