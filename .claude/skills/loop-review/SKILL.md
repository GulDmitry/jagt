---
name: loop-review
description: Find why jagt stopped a task (the card's `lift the stop`) and fix the cause, one stop at a time from `stops.jsonl`. Use when a card shows `lift the stop`, a `task stopped` line appears in the log, or the refresh reaches its loop step.
---

# A stop is a defect until shown otherwise

Input: the lines of `stops.jsonl` (beside `state.json`) with no line of their own in `stops-reviewed.jsonl`.

1. **Read jagt's records only**: the task in `state.json` (`stop`, `trail`, `history`, `refusals`), the worktree's
   `.jagt/master-review.json` and `master-rounds.json`, `task_context.md`, `task_notes.md`, `misses.jsonl`, and the
   `jagt-backend.log*` lines of the task around `at`. Never a ticket's or a customer's business content.
2. **Name who looped**, one of: the session (asked again without new work), the Master (sent the same verdict on new
   work, or a round no work could pass), a case (no case moves the task on), jagt (a sweep, a count or a stale read).
   Rounds whose input changed (a new commit, new notes, a different verdict) are work, not a loop: the fix then is a
   count that only counts identical rounds, as the trail's fingerprint already does for checks.
   A loop may alternate (asks A, gets X, asks B, gets Y, asks A again): compare each round with every earlier one,
   not only the last; a pair that recurs with nothing new between is a loop.
3. **Name the class, not the case**, in two to four words, as `docs/cases.md` names its families.
4. **Fix where it belongs**:
   - Master → `master-lesson`.
   - session → the brief or the rule it broke, proven by `promptEval`.
   - case → `cases` spec plus a case.
   - jagt → a regression test verified RED, a `docs/cases.md` row, a `Defect-family` trailer.
   - A stop that rightly stopped (the human really had to decide) gets no fix, with one clause why.
5. **Append one line to `stops-reviewed.jsonl`**: `{"at":…, "task":…, "who":…, "class":…, "fix":"<sha or why none>"}`.
6. Tell the human which stops may be lifted now, and what changed for each.
