# Decisions

[← AGENTS.md](../../AGENTS.md)

One file per ARCHITECTURAL decision — jagt's rings and seams, where state lives, how sessions run, what owns a
move: costly to reverse, or one a newcomer would undo as wrong. Never a feature's rule or parameter.
The rule lives in `openspec/specs/` or `docs/rules/`; a record keeps its evidence and alternatives.

- Named `NNNN-what-is-decided.md`, numbered in order, never renumbered.
- **Never edited once accepted**, typos aside: a change is a new record, the old one's status `superseded by NNNN`.
- Sections: **Status** (date, state), **Context** (what forced it, with numbers),
  **Decision**, **Rejected** (each with its reason), **Reopen when** (what measurement changes it).
- At most 400 words, or it is two decisions.
- **Linked**: a record names its rule first; this index, where a why is recovered, lists every record
  (`DecisionIndexTest`).
- A record taken after the fact says `recorded retroactively`, carrying only what history proves.

## Index

- [0001 — A session lives one round](0001-a-task-session-lives-one-round.md)
- [0002 — The board is jagt's only UI](0002-the-board-is-jagts-only-ui.md)
- [0003 — Tracker, code host via MCP](0003-tracker-and-code-host-are-read-through-the-agents-own-mcp.md)
- [0004 — Invariants stay out of git hooks](0004-jagt-keeps-its-invariants-out-of-git-hooks.md)
- [0005 — The base branch is read-only](0005-the-base-branch-is-read-only.md)
- [0006 — The machine moves; a model only judges](0006-the-machine-owns-every-move-a-model-only-judges.md)
- [0007 — Only adapter names a vendor or an OS](0007-only-adapter-names-a-vendor-or-an-os.md)
- [0008 — A failed read is never "not found"](0008-a-failed-read-is-never-not-found.md)
- [0009 — Read once and quoted](0009-what-jagt-can-read-it-reads-once-and-quotes.md)
- [0010 — The human overrules the Master](0010-the-humans-own-word-stands-over-the-master.md)
- [0011 — A red run is the session's to diagnose](0011-a-red-run-is-the-sessions-to-diagnose.md)
- [0012 — The Master finishes the task](0012-the-master-finishes-the-task.md)
- [0013 — Deploy conflict, session's in `act`](0013-a-deploy-conflict-is-the-sessions-in-act.md)
- [0014 — Behaviour lives in OpenSpec](0014-behaviour-is-recorded-as-openspec-specs.md)
- [0015 — Another branch is another task](0015-another-branch-is-another-task.md)
- [0016 — Only the human closes a task](0016-only-the-human-closes-a-task.md)
- [0017 — The Master in one feed window](0017-the-master-is-seen-in-one-feed-window.md)
- [0018 — The Master judges by default](0018-the-master-judges-by-default.md)
- [0019 — Large work cut by spec](0019-large-work-is-cut-by-its-spec-not-split-into-requests.md)
- [0020 — jagt holds no token](0020-jagt-holds-no-token-of-its-own.md)
- [0021 — jagt runs from its clone](0021-jagt-runs-from-a-clone-of-its-repository.md)
- [0022 — The one rewrite](0022-the-resume-rebase-is-the-one-rewrite-and-the-guard-is-a-guardrail.md)
- [0023 — The Master holds a token](0023-the-master-is-identified-by-a-token.md)
- [0024 — Calls metered where run](0024-every-headless-call-is-metered-where-it-runs.md)
- [0025 — Token-guarded board writes](0025-the-board-is-no-way-around-the-token.md)
- [0026 — Guard gaps, round three](0026-the-guards-gaps-after-round-three.md)
- [0027 — Headless calls are fenced](0027-a-headless-call-is-fenced-by-jagt.md)
- [0028 — Guard gaps, four](0028-what-the-guards-still-pass-after-round-four.md)
- [0029 — Guard gaps, five](0029-what-the-guards-still-pass-after-round-five.md)
- [0030 — Guard gaps, six](0030-what-the-guards-still-pass-after-round-six.md)
- [0031 — Host CLI anywhere](0031-the-host-cli-is-judged-wherever-it-stands.md)
