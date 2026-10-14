# 0040 — Git runs only what the human trusted

[← decisions](README.md) · rule: [`guards`](../rules/guards.md)

## Status

Accepted 2026-10-14. Closes two rows of [0028](0028-what-the-guards-still-pass-after-round-four.md) and
[0029](0029-what-the-guards-still-pass-after-round-five.md): config the reviewer's git obeys, and shared config set
from a worktree, run in the human's checkout.

## Context

- A session can write the repository's shared `.git/config`, its own `config.worktree` and `.git/hooks`. `deploy` and
  `revert` then run git there with the human's credentials, `ship` pushes with them, and the Master's reviewer runs
  `git diff` in the worktree. `core.fsmonitor`, `gpg.program`, `diff.external` or a hook each run a program.
- A peer orchestrator recorded a worker setting `http.sslVerify=false`; jagt had no record to compare against.

## Decision

- Each task carries `GitTrust` in `state.json`: one SHA-256 per config key and file, and per hook file, never a
  value. It is read as the worktrees are cut, removed with the task, and folds to one digest per file past 512.
- Before `ship`, `deploy`, `revert` and a Master read, `GitStateGuard` and `ChargedReviews` compare. A difference
  refuses, names key and file, and stays on the task: its card is the human's, `trust` the move.
- `trust` is a human-only verb from `Move.actions()`; nothing else adopts a change.
- Detection, not prevention: no hook in a repository ([0004](0004-jagt-keeps-its-invariants-out-of-git-hooks.md)).
- A task with nothing recorded trusts what its first check reads.

## Rejected

- Whole-file digests: a refusal could not name the key.
- A block-list of dangerous keys: a key nobody named passes.
- The record in the worktree: the session writes there.
- Tracking keys git writes on a push (`branch.*.remote`, `.merge`): every ship would park.

## Reopen when

- A guarded git runs a program set in global, system or included config.
- Parking fires on a change no human would refuse.
