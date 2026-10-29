# 0102 — A request is merged when its tip reached the target in git

[← decisions](README.md) · rule: [`git`](../../openspec/specs/git/spec.md), [`task-flow`](../../openspec/specs/task-flow/spec.md)

## Status

Accepted 2026-10-28. Adds to [0061](0061-jagt-stops-a-task-it-suspects-it-is-looping.md).

## Context

- The Master shipped ABC-42 three times on open request !571 with nothing new: a real loop, counted.
- !571 merged, the Master shipped a fourth time, and the loop stop tripped on a request that no longer existed.
- jagt held no "merged" fact: `ReviewFacts` and `TaskRepo` knew an open request, `Move` offered `ship` whenever one existed.

## Decision

- A request is merged when, after `git fetch`, a merge commit of `origin/<target>` took the branch tip in as a parent.
- A branch that never diverged is inside its target too, and is not merged: the tip must be a non-first parent.
- `TaskRepo.merged` keeps it; `Facts.requestMerged` is true when every repository has a request and each is merged.
- `ship` is not offered on such a task, and `MergedGuard` refuses a press whose fresh read finds it merged, recording no step.
- The review sweep and the ship press read it; a merged request is not read again, a new request link clears it.

## Rejected

- The model host reader: a loop guard must hold when a model is the loop, and the read would be paid for per sweep.
- A host API port: the right source for squash and rebase merges, a later step.
- A flag on `StatusChange`: with the press refused no ship step is written after a merge, so there is nothing to mark.

## Known limits

- A squash, a rebase or a fast-forward merge leaves no merge commit naming the tip: git alone never sees it.
- The review read carries the host's own `merged` word, which records it too.

## Reopen when

- The host's `merged` word proves wrong: a read said merged for an open request.
