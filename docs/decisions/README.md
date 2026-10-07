# Decisions

[← AGENTS.md](../../AGENTS.md)

One file per ARCHITECTURAL decision — the shape of jagt: its rings and seams, where state lives, how sessions
run, what owns a move. Costly to reverse, or one a newcomer would undo because it looks wrong. A feature's rule or
parameter is not one: it lives in its spec and git history.
The rule itself lives in `openspec/specs/` or `docs/rules/`; a record keeps what the rule may not — the evidence and the
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

- [0001 — A task session lives one round](0001-a-task-session-lives-one-round.md)
- [0002 — The board is jagt's only UI](0002-the-board-is-jagts-only-ui.md)
- [0003 — Tracker and code host via the agent's own MCP](0003-tracker-and-code-host-are-read-through-the-agents-own-mcp.md)
- [0004 — jagt keeps its invariants out of git hooks](0004-jagt-keeps-its-invariants-out-of-git-hooks.md)
- [0005 — The base branch is read-only](0005-the-base-branch-is-read-only.md)
- [0006 — The machine owns every move; a model only judges](0006-the-machine-owns-every-move-a-model-only-judges.md)
- [0007 — Only adapter names a vendor or an OS](0007-only-adapter-names-a-vendor-or-an-os.md)
- [0008 — A failed read is never "not found"](0008-a-failed-read-is-never-not-found.md)
- [0009 — What jagt can read, it reads once and quotes](0009-what-jagt-can-read-it-reads-once-and-quotes.md)
- [0010 — The human's own word stands over the Master](0010-the-humans-own-word-stands-over-the-master.md)
- [0011 — A red run is the session's to diagnose](0011-a-red-run-is-the-sessions-to-diagnose.md)
- [0012 — The Master finishes the task](0012-the-master-finishes-the-task.md)
- [0013 — A deploy conflict is the session's in `act`](0013-a-deploy-conflict-is-the-sessions-in-act.md)
- [0014 — Behaviour is recorded as OpenSpec specs](0014-behaviour-is-recorded-as-openspec-specs.md)
- [0015 — Another branch is another task](0015-another-branch-is-another-task.md)
- [0016 — Only the human closes a task](0016-only-the-human-closes-a-task.md)
