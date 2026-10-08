# Testing etiquette

[← AGENTS.md](../../AGENTS.md)

`./gradlew test` is the hermetic gate. Each other suite lives in `src/<name>/java`, none in `check`: `e2eTest`
needs git + tmux; `boardTest` Playwright's own Chromium; `linuxDriverTest` Linux + binaries + a display (gated on
`JAGT_IN_CONTAINER`); `promptEval` the assistant's CLI and tokens; `masterEval` those plus minutes.

**Every fixed bug gets a regression unit test** (`sob-ai:unit-testing`), verified RED by reverting the fix, and
**every new install requirement goes in `docs/installation.md`**. **Leave no trace**: a suite booting the app
passes `--orchestrator.open-terminal-window=false`, uses a throwaway tmux session plus `ORCHESTRATOR_ROOT`,
then removes the worktrees and branches. A test reading the repository's own files may keep **file-scanning**
helpers (`TextBudgetTest.mainSources`, `ConfigReferenceTest.javaFilesUnder` …). A shared **fake** of a production
seam (`ScriptedProcesses`) is allowed, a shared builder is not.

### Bare binary names, and a concurrent unit suite

- An external binary is configured by **bare name**; `adapter/Executables` resolves it against PATH, install
  directories (Homebrew included), per-user script dirs, then **inside application bundles**. An absolute
  default fails every task on Linux; `editor-command` defaults to bare `idea`.
- **Do not weaken the resolver**: an IDE needs the bundle step
  (`/Applications/<App>.app/Contents/MacOS/<name>`). The agent CLI is **not** resolved: it runs under the
  human's PATH in its tmux window.
- `editor-command` / `editor-diff-command` are **lists**: only the launcher is resolved, the arguments stay the
  human's; a launcher found nowhere fails naming the config **key**.
- JUnit parallel, methods **and** classes: no `@BeforeAll`, no mutable statics, every file under a `@TempDir`;
  anything competing for a **machine-wide** resource declares it (`@ResourceLock("loopback-ports")` +
  `@Execution(SAME_THREAD)`). Only this suite is parallel; it runs in Turkish, so case changes take `Locale.ROOT`.
- **A test that asserts on a log line takes `@ResourceLock(Resources.GLOBAL)`**: else its appender captures a
  concurrent test's events and the `Logger` cast races SLF4J's start-up.

### The board is tested in a browser

- `boardTest` drives the real page, on a random port, in headless Chromium. Only it
  proves the grid's order, a card's buttons, the SSE repaint and the palette's verdict.
- **Run it after any change to `static/`**, asserting through the **server** (seed `StateService`, stub a
  command), never by evaluating page JS. Its `@MockitoBean`s stub whole-app seams, never a class's
  collaborators: `CommandService`, `TaskLauncher`, `NaturalLanguageDispatch`, `AgentSessions`, `EditorDriver`,
  a `ReviewRepliesReport` spy; e2e's stub only ports.
- Shared browser libraries are one list (`scripts/linux-test-deps.sh`). Assert geometry as an element inside the
  viewport at a set size, never a screenshot.

### The e2e matrix and the model evals

- `e2eTest` runs the flow per `TaskFlowCase` under `orchestrator.agent.cli=stub` (`StubAgentRuntime`, GUI
  drivers doubled), asserting an exact end state. Widening coverage is a **row** in `TaskFlowCase.matrix()`;
  an uncovered one is named there, with why.
- It asserts the **sentence** a flow returns: run it before pushing a reword.
- Two matrices: `TaskFlowCase` × `TaskFlowMatrixTest` is CREATE → TEARDOWN across the viewer combinations;
  `ReviewRoundCase` × `ReviewAndDeployFlowTest` is everything between (ship, a round, deploy, revert, resume)
  on **one** combination. Its verbs go through the board's HTTP endpoints, the agent reports over
  `POST /mcp`: origins (`board` vs `mcp`) are asserted end to end.
- `promptEval` puts one operator phrasing per row (`CommandMappingCase`) through the real assistant: run it on a
  change to the mapping prompt, a `TaskAction`'s hint, the task-list shape or the model.
- `masterEval` hands the real session a round whose verdict is known (`MasterCase`), judged by the shipped
  `master-brief.md.dist`. Run it when either changes; a defect it passed: `master-lesson`.

### Linux, and one set of steps per host

- `scripts/linux-suite.sh` runs `test` + `e2eTest` + `boardTest` + `linuxDriverTest` in a container
  (`docker/linux-suite.Dockerfile`), the only place the Linux drivers meet real binaries:
  the notifier asserted off the session bus with `dbus-monitor`, kitty under Xvfb.
- What a container cannot host — IntelliJ, the AppleScript raise, the real `claude` — stays **named as
  uncovered**, never faked; raising and closing the viewer stay `@Disabled`
  (`LinuxKittyTerminalDriverLinuxTest`).
- `.github/workflows/ci.yml` and `.gitlab-ci.yml` run the same suites through the **same scripts**
  (`scripts/linux-test-deps.sh` the package list, `scripts/with-linux-desktop.sh` Xvfb + session bus +
  notification daemon). **A step in one pipeline only, or a CI-only code path, is a bug.** `linuxDriverTest` is
  gated on **capability**, never on the harness.
- **Only the hermetic suite is cached**: three others prove the **machine**, two a model
  (`cacheIf` / `upToDateWhen` false).
