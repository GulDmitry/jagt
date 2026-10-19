# Decisions

[← AGENTS.md](../../AGENTS.md)

One file per ARCHITECTURAL decision — jagt's rings and seams, where state lives, how sessions run, what owns a
move: costly to reverse, or one a newcomer would undo as wrong. Never a feature's rule or parameter.
The rule lives in `openspec/specs/` or `docs/rules/`; the record, its evidence and alternatives.

- Named `NNNN-what-is-decided.md`, numbered in order, never renumbered.
- **Never edited once accepted**, typos aside: a change is a new record, marking the old `superseded by NNNN`.
- Sections: **Status** (date, state), **Context** (what forced it, with numbers),
  **Decision**, **Rejected** (each with why), **Reopen when** (the measurement changing it).
- At most 50 lines, else two decisions.
- **Linked**: a record names its rule first; this index lists all (`DecisionIndexTest`).
- Taken late: `recorded retroactively`, only what history proves.

## Index

- [0001 A session lives one round](0001-a-task-session-lives-one-round.md)
- [0002 The board is jagt's only UI](0002-the-board-is-jagts-only-ui.md)
- [0003 Tracker, code host via MCP](0003-tracker-and-code-host-are-read-through-the-agents-own-mcp.md)
- [0004 Invariants out of git hooks](0004-jagt-keeps-its-invariants-out-of-git-hooks.md)
- [0005 The base branch is read-only](0005-the-base-branch-is-read-only.md)
- [0006 Machine moves, model judges](0006-the-machine-owns-every-move-a-model-only-judges.md)
- [0007 Only adapter names vendor, OS](0007-only-adapter-names-a-vendor-or-an-os.md)
- [0008 Failed read is not missing](0008-a-failed-read-is-never-not-found.md)
- [0009 Read once and quoted](0009-what-jagt-can-read-it-reads-once-and-quotes.md)
- [0010 Human overrules Master](0010-the-humans-own-word-stands-over-the-master.md)
- [0011 Red run, session diagnoses](0011-a-red-run-is-the-sessions-to-diagnose.md)
- [0012 Master finishes task](0012-the-master-finishes-the-task.md)
- [0013 Deploy conflict, session's in `act`](0013-a-deploy-conflict-is-the-sessions-in-act.md)
- [0014 Behaviour lives in OpenSpec](0014-behaviour-is-recorded-as-openspec-specs.md)
- [0015 Another branch is another task](0015-another-branch-is-another-task.md)
- [0016 Only the human closes](0016-only-the-human-closes-a-task.md)
- [0017 The Master in one feed window](0017-the-master-is-seen-in-one-feed-window.md)
- [0018 Master judges by default](0018-the-master-judges-by-default.md)
- [0019 Large work cut by spec](0019-large-work-is-cut-by-its-spec-not-split-into-requests.md)
- [0020 jagt holds no token](0020-jagt-holds-no-token-of-its-own.md)
- [0021 jagt runs from its clone](0021-jagt-runs-from-a-clone-of-its-repository.md)
- [0022 The one rewrite](0022-the-resume-rebase-is-the-one-rewrite-and-the-guard-is-a-guardrail.md)
- [0023 The Master holds a token](0023-the-master-is-identified-by-a-token.md)
- [0024 Calls metered where run](0024-every-headless-call-is-metered-where-it-runs.md)
- [0025 Token-guarded board writes](0025-the-board-is-no-way-around-the-token.md)
- [0026 Guard gaps, three](0026-the-guards-gaps-after-round-three.md)
- [0027 Headless calls are fenced](0027-a-headless-call-is-fenced-by-jagt.md)
- [0028 Guard gaps, four](0028-what-the-guards-still-pass-after-round-four.md)
- [0029 Guard gaps, five](0029-what-the-guards-still-pass-after-round-five.md)
- [0030 Guard gaps, six](0030-what-the-guards-still-pass-after-round-six.md)
- [0031 Host CLI anywhere](0031-the-host-cli-is-judged-wherever-it-stands.md)
- [0032 Reviews keep decisions](0032-a-global-review-is-judged-against-what-is-decided.md)
- [0033 Usage via login](0033-jagt-reads-the-clis-own-login-for-usage-and-keeps-none.md)
- [0034 Null declared](0034-null-is-declared-and-the-compiler-checks-it.md)
- [0035 Callerless units](0035-a-unit-never-names-who-uses-it.md)
- [0036 Bytecode structure checks](0036-structure-is-checked-on-the-bytecode.md)
- [0037 One-shot caches minutes](0037-a-one-shot-caches-for-minutes.md)
- [0038 First role writes the round](0038-a-panels-first-role-writes-the-round.md)
- [0039 No JVM flag](0039-jagt-starts-with-no-jvm-flag.md)
- [0040 Git runs what was trusted](0040-git-runs-only-what-the-human-trusted.md)
- [0041 jagt runs a plan's probes](0041-a-plans-probes-are-run-by-jagt.md)
- [0042 Kept paths stay human](0042-a-path-list-keeps-a-step-with-the-human.md)
- [0043 `act` stops itself](0043-act-stops-itself-after-three-bad-endings.md)
- [0044 Sandboxed writes](0044-a-session-writes-only-inside-its-task.md)
- [0045 Bounded stores](0045-every-store-jagt-writes-has-a-ceiling.md)
- [0046 Preact, no build](0046-the-board-renders-preact-components-with-no-build.md)
- [0047 The editor is a launcher](0047-the-editor-is-a-launcher.md)
- [0048 Spend ceiling](0048-every-headless-call-has-a-spend-ceiling.md)
- [0050 Wake on reset](0050-a-session-stopped-on-a-full-window-is-woken-once.md)
