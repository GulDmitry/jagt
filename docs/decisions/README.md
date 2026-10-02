# Decisions

[← AGENTS.md](../../AGENTS.md)

One file per fundamental decision: costly to reverse, or one a newcomer would undo because it looks wrong.
The rule itself lives in `docs/rules/`; a record keeps what the rule may not — the evidence and the
alternatives — so a later reader can tell a decision from a habit.

- Named `NNNN-what-is-decided.md`, numbered in order, never renumbered.
- **Never edited once accepted**, typos aside: a changed decision is a new record, and the old one's status
  becomes `superseded by NNNN`.
- Sections: **Status** (date, accepted or superseded), **Context** (what forced it, with numbers),
  **Decision**, **Rejected** (each with its one reason), **Reopen when** (the measurement that would change it).
- At most 400 words: a record needing more is two decisions.
- **Linked**: a record names the rule it backs at its top; this index lists every record (`DecisionIndexTest`). A
  session recovering why something holds starts here, then follows the record's commits.
- A record taken after the fact says `recorded retroactively` and carries only what the history proves.

## Index

| record | rule |
|--------|------|
| [0001 — A task session lives one round](0001-a-task-session-lives-one-round.md) | [`runtime.md`](../rules/runtime.md) |
| [0002 — The board is the only human surface](0002-the-board-is-the-only-human-surface.md) | [`surfaces.md`](../rules/surfaces.md) |
| [0003 — Tracker and code host are read through the agent's own MCP](0003-tracker-and-code-host-are-read-through-the-agents-own-mcp.md) | [`seams.md`](../rules/seams.md) |
| [0004 — No git hook in a project's repository](0004-no-git-hook-in-a-projects-repository.md) | [`git.md`](../rules/git.md) |
| [0005 — The base branch is read-only](0005-the-base-branch-is-read-only.md) | [`git.md`](../rules/git.md) |
| [0006 — The machine owns every move; a model only judges](0006-the-machine-owns-every-move-a-model-only-judges.md) | [`flow.md`](../rules/flow.md) |
| [0007 — Only adapter names a vendor or an OS](0007-only-adapter-names-a-vendor-or-an-os.md) | [`seams.md`](../rules/seams.md) |
| [0008 — Tasks are capped at 24, with no queue](0008-tasks-are-capped-at-24-with-no-queue.md) | [`AGENTS.md`](../../AGENTS.md) |
| [0009 — A failed read is never "not found"](0009-a-failed-read-is-never-not-found.md) | [`seams.md`](../rules/seams.md) |
