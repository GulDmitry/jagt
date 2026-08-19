# jagt — TODO

Nothing open. Rules live in CLAUDE.md; situations in USE-CASES.md; decisions live in the code and git history.

## Modularisation — split into wired modules with a configurable order (raised 2026-08-19, owner's call open)

- Pluggable by config is ALREADY the architecture: six seams (`UserNotifier`, `TerminalDriver`, `EditorDriver`,
  `AgentRuntime`, `CodeHost`, `Tracker`) selected by a config value, with Spring as the container. Nothing to build.
- ORDER AS CONFIG: no. The flow is a cyclic state machine with human gates, not a pipeline — ship → CI → round →
  ship again, deploy → conflict → deploy again, revert → ship. `Move.forTask(status × report)` IS the order, total
  and exhaustively switched. A wiring file is either a facade over one shared `state.json` (the dissolved
  `OrchestratorTools`, with a config file instead of a class) or a second state machine in YAML. And the
  reorderable steps are the safety rules — nothing pushed without a human `ship`, writes only to `deployBranch`,
  one sweep per task — so a configurable order turns each of them into wiring nobody tests.
- JAR PLUGINS (`ServiceLoader`, own classloader): no. Buys third-party driver distribution, which does not exist
  here; costs a frozen public SPI, classloader debugging, a second answer to "how is an impl selected", and the
  one-jar/offline property.
- GRADLE MULTI-MODULE: it is ENFORCEMENT, not architecture — a compiler that stops `service` reaching into
  `platform.macos`. At 142 files / 13k lines the build topology costs more than it returns, and every module would
  land in the same jar and the same Spring context. OPEN: take the same enforcement for nothing — ArchUnit package
  rules in the hermetic suite (`model` depends on nothing; `platform.*` names no service; a seam impl never calls
  orchestration).
- What the idea points at correctly, and no module boundary fixes: `MasterShell` 802 lines, `GitService` 820.
  That is decomposition, not packaging.
