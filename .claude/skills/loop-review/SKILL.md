---
name: loop-review
description: Run BEFORE answering anything about a stopped task — a pasted card with `lift the stop`, a question why a task stopped or whether to lift, a `task stopped` log line, or the refresh's loop step. Finds who looped and fixes the cause, and records each stop with its lift verdict in `stops-reviewed.jsonl`; no lift is advised before that line exists.
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
5. **Append one line to `stops-reviewed.jsonl`**: `{"at":…, "task":…, "trigger":…, "who":…, "class":…, "fix":"<sha or why none>",
   "lift":"<safe, or what must be true first>"}`. A stop of a class already in the file reuses its class and lift verdict.
6. Tell the human which stops may be lifted now, and what changed for each. No lift is advised before its line exists.
7. **A verdict that changes when a class may be lifted rewrites that trigger's `next` in `task/TaskStop`**, in the
   fix's commit, so every install reads it on the card's hover. Generic words only: no task, project, path, host or
   vendor of this machine; the cases that taught it stay in `stops-reviewed.jsonl`.
