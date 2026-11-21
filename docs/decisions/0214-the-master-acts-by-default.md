# 0214 — The Master acts by default

[← decisions](README.md) · rule: [`master`](../../openspec/specs/master/spec.md)

## Status

Accepted 2026-11-21 by the owner's order of 2026-11-20. Supersedes [0018](0018-the-master-judges-by-default.md). Re-read:
[0043](0043-act-stops-itself-after-three-bad-endings.md) and [0140](0140-in-act-the-master-decides-a-stop-for-a-suspected-loop.md) stand unchanged;
[0064](0064-a-plan-the-human-asked-for-is-theirs.md)'s `[plan]` default grows to the list below.

## Decision

- An unset `roles.master.mode` is `act` where the runtime reads the human's words, else `off`.
- An unset `roles.master.mine` is `[plan, deploy, revert]`: the plan approval and the deploy stay the human's, the rest the Master's.
  `revert` rides with `deploy`: both write the same shared branch (`MasterCheck`).
- `judge` and `off` stay one written word away; both `.dist` starters describe the defaults.
- `FlowRules` still bound the legal moves, the base branch stays read-only, `act` stops itself after three bad endings.

## Rejected

- A new `humanKeeps` key: `mine` is already the per-move list.
- `act` with `mine` empty by default: a deploy writes the shared branch, which stays a human press until the human hands it over.

## Reopen when

- The Master's `act` moves are overruled more often than 0018's `judge` verdicts were.
