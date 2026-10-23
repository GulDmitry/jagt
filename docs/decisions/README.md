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
- [0049 Daily start ceiling](0049-unattended-starts-have-a-daily-ceiling.md)
- [0050 Wake on reset](0050-a-session-stopped-on-a-full-window-is-woken-once.md)
- [0051 One constructor](0051-a-class-has-one-constructor-and-tests-use-it.md)
- [0052 Typed endings](0052-an-ending-says-which-one.md)
- [0053 Cognitive complexity 12](0053-a-method-reads-at-cognitive-complexity-fifteen-at-most.md)
- [0054 Artifacts on the board](0054-a-tasks-artifacts-are-read-on-the-board.md)
- [0055 English and UTC](0055-jagt-speaks-english-and-keeps-utc.md)
- [0056 A report changes nothing](0056-a-report-to-jagt-changes-nothing.md)
- [0057 Edit tools write where the shell may](0057-the-edit-tools-write-where-the-shell-may.md)
- [0058 A test never reaches the terminal](0058-a-test-never-reaches-the-developers-terminal.md)
- [0059 Plan first is jagt's gate](0059-plan-first-is-jagts-gate-not-the-clis-plan-mode.md)
- [0060 The Session tab stays beside focus](0060-the-session-tab-stays-beside-focus.md)
- [0061 jagt stops its own loop](0061-jagt-stops-a-task-it-suspects-it-is-looping.md)
- [0062 A permission is the human's to grant](0062-a-permission-is-the-humans-to-grant.md)
- [0063 A stalled deploy conflict is the human's](0063-a-stalled-deploy-conflict-is-the-humans.md)
- [0064 A plan you asked for is yours](0064-a-plan-the-human-asked-for-is-theirs.md)
- [0065 One contract for jagt's own texts](0065-jagts-own-texts-and-loops-are-held-to-one-contract.md)
- [0066 Only you open a task](0066-only-the-human-opens-a-task.md)
- [0067 Pings follow the owner's flip](0067-pings-follow-the-owner-flip.md)
