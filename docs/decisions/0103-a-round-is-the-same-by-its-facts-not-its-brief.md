# 0103 — A round is the same by its facts, not by its brief

[← decisions](README.md) · rule: [`review`](../../openspec/specs/review/spec.md)

## Status

Accepted 2026-10-28. Amends [0061](0061-jagt-stops-a-task-it-suspects-it-is-looping.md): the digest it keeps is of the
round's facts, not of the brief.

## Context

- The same failing job on the same commit was relayed at 10:20 and again at 11:23, three times in 24h.
- The failure text is a model's reading of a log, so two reads differ by a few characters and so did their briefs.
- A ROUNDS stop followed, on a round that had never changed.

## Decision

- `TaskStops.round` digests the failing job, the branch tip git holds pushed and the open threads' newest notes.
- The tip is `origin/<task branch>` per repository (`WorktreeInspection.pushedTip`), read by git, never by the host's model.
- The checks' wording and the threads' copied text no longer count.
- Where git cannot name a tip, a red round has no job name, or open threads have no note ids, the digest is of the brief.

## Rejected

- Digesting the failure's lines: a model's wording is the very noise.
- The job's name alone: a new commit failing the same job differently would never reach the session.
- The head commit read by the host's model: a non-deterministic read keying a deterministic guard.

## Reopen when

- A round moves on a commit that `origin/<branch>` does not hold, e.g. a push by another route: the key then hides it.
