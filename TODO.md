# jagt — TODO

## Unify the command shape so a new capability is one class (raised 2026-08-19, owner's call open)

The asymmetry, not modules, is the real defect. PER-TASK verbs are already unified: `TaskAction` (9 rows) →
`Move.forTask` decides availability → `CommandService.execute` is the one dispatch → both surfaces render
`Move.actions()`. Adding one is four edits and no surface has to be told.

NON-TASK commands (`do`, `resume`, `stats`, `activity`, `help`, `status`) share none of that. Each is hand-written
in six places: an arm in `GrammarDispatch`, a `Verb` in `CommandReference.verbs()`, a prose line in
`CommandReference.text()` that repeats the same hint, a rank in `BY_USE`, its own endpoint
(`/api/activity|help|stats`, `POST /api/tasks[/resume]`), and its own wiring in `app.js`. Every parity bug jagt
has had was in THIS class and never in the other — `resume`/`stats`/`help` were console-only until 2026-08-13,
`activity` until 2026-08-18.

- DO: one `Command` contract — id, hint, usage, a gate over state, `execute(context) → sentence` — declared into a
  registry, exactly the `McpTools`/`McpToolRegistry` shape the dissolution of `OrchestratorTools` already settled
  on. `GrammarDispatch` looks up instead of switching; `CommandReference.text()` is GENERATED; the board reads
  `/api/commands` (it already exists) instead of naming verbs in JS. Then a capability is one class plus a test.
- Input/output stays what it is: `(context) → String`. The context carries the resolved task (optional), the parsed
  tail and the origin. The sentence IS the human answer, `Refusal.Code` the machine one — no new result record.
- DO NOT unify the argument parsing: `do` takes six modifiers, `resume` a URL, `stats` nothing. One arg spec for
  all of them becomes a parser DSL. The registry owns id/hint/usage/gate/execute; each command parses its own tail.
- Registry completeness is a TEST, not a type: every registered command must appear in both surfaces' output —
  the `McpToolScopeTest` pattern, one row per command.

## ArchUnit — enforce the rules already written down (open)

There is no layered architecture here, so cycle/layer rules are meaningless. What is mechanically checkable is the
prose CLAUDE.md already asserts and nothing verifies:

- the collaborator ceiling: ≤5 constructor parameters, target 3. Counted by hand once ("checked 2026-08-14: 70
  classes"); the tree is 142 files now.
- `model` depends on nothing but `model` and the JDK — both surfaces read that projection.
- nothing outside `agent.*` names an agent's own files (`.mcp.json`, `.codex`, `CLAUDE.md`); nothing outside
  `platform.macos` names `osascript`; nothing outside `TtydWebTerminal` names ttyd.
