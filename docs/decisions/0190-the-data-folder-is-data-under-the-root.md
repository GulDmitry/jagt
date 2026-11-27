# 0190 — The data folder is `data/` under the root

[← decisions](README.md) · supersedes the default of [0168](0168-one-data-folder-holds-everything-jagt-writes.md)

## Status

Accepted 2026-11-17.
Default superseded by [0266](0266-the-data-folder-lies-outside-the-checkout.md): `~/.jagt/data`, not `<root>/data`.

## Context

- 0168 defaulted to `~/.jagt`: a second place to look, outside the folder the owner runs jagt from.
- One gitignored folder beside `jagt.yml` holds every write, so the root's `.gitignore` needs one line.

## Decision

- `orchestrator.data` defaults to `<root>/data`.
- The launch pins `orchestrator.default-data`, so `application.yml` names the log paths before any bean exists.
- `ORCHESTRATOR_DATA` and `JAGT__orchestrator__data` still win; the container keeps `/data`.
- The tree is grouped by owner and lifetime, built only by `Layout`: `jagt/` (state and marks), `projects/<name>` (clones),
  `tasks/<task>/` (`worktrees/<project>`, `plan-baseline.md`, `ticket-text.md`, `scratch/`), `archive/`, `records/`, `memory/`, `logs/`.
- `DataFolderStart` moves what ONE older folder kept, the first of the data folder (flat), `~/.jagt` (`orchestrator.previous-data`) and the
  root holding `state.json`, to its place; none moves where `jagt/state.json` exists. It logs `older file moved into the data folder` per file,
  warns on a name already in its place and keeps the older file. A second start moves nothing.
- A moved clone has its recorded worktrees repaired (`git worktree repair`), so a task in flight keeps its checkout.
- The older `master-token` is deleted: a stale credential, redrawn at each start.
- A finished task's folder moves into its own snapshot, or a fresh one, never over a snapshot's copy; one still holding a worktree is
  shelved when the orphan deletion that removes the worktree ends it.
- The orphan scan also reads the older `<data>/worktrees/<project>/` folder.
- `home/` and `cache/` have no writer today; `Layout` gains each with its first.
- Worktrees and scratch folders of existing tasks stay where `state.json` records them; `master-token` is not moved.

## Rejected

- Moving `~/.jagt` as a folder: its worktrees are recorded by absolute path in `state.json`; a move would break them.
- Flat names inside the folder: a task's files would sit in four places.

## Reopen when

- A path under the folder is built anywhere but `Layout`.
