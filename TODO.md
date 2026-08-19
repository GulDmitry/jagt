# jagt — TODO

## ArchUnit — enforce the rules already written down (open)

There is no layered architecture here, so cycle/layer rules are meaningless. What is mechanically checkable is the
prose CLAUDE.md already asserts and nothing verifies:

- the collaborator ceiling: ≤5 constructor parameters, target 3. Counted by hand once ("checked 2026-08-14: 70
  classes"); the tree is 142 files now.
- `model` depends on nothing but `model` and the JDK — both surfaces read that projection.
- nothing outside `agent.*` names an agent's own files (`.mcp.json`, `.codex`, `CLAUDE.md`); nothing outside
  `platform.macos` names `osascript`; nothing outside `TtydWebTerminal` names ttyd.
