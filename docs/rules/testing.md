# Testing etiquette

[← AGENTS.md](../../AGENTS.md)

`./gradlew test` is the hermetic gate, and **a cached green is not a run**: at a batch's end and before a push it is
`./gradlew test --rerun-tasks`. Each other suite lives in `src/<name>/java`, none in `check`: `e2eTest` needs git +
tmux; `boardTest` Playwright's own Chromium; `linuxDriverTest` Linux + binaries + a display (gated on `JAGT_IN_CONTAINER`);
`promptEval` the assistant's CLI and tokens; `roleEval -Prole=<id>` those plus minutes. **A commit changing `main` runs
`./gradlew pitestDiff -PpitestDiff`**: its writer judges each mutant surviving on a changed line
([0125](../decisions/0125-mutants-are-judged-on-the-diff-at-each-commit.md)).

**Every fixed bug gets a regression unit test** (`sob-ai:unit-testing`), verified RED by reverting the fix. Its
`docs/cases.md` row and `Defect-family:` trailer make defects countable per family. **Every new install requirement goes in
`docs/installation.md`**. **Leave no trace**: a suite booting the app passes `--orchestrator.open-terminal-window=false`,
uses a throwaway tmux session plus `ORCHESTRATOR_ROOT`, then removes the worktrees and branches; every `Test` task runs on a
tmux server of its own (`TMUX_TMPDIR`, killed after the task), so no test reaches the human's. A test reading the
repository's own files may keep **file-scanning** helpers (`TextBudgetTest.mainSources`, `ConfigReferenceTest.javaFilesUnder`
…). A shared **fake** of a production seam (`ScriptedProcesses`) is allowed, a shared builder is not.

### Every test pins a business case

- A test pins what a spec scenario, a ticket or a contract promises: a serialised answer's shape is one.
- Code that only forwards or holds — a getter, a controller calling a service — gets no test; the business case through it covers it.
- Editing a test's assertion or expected value, or deleting it, needs a reason in the commit message.
- The reason: its scenario changed in the same commit, or it asserted what nothing promised; never a red run, the code's to fix.
- **A move is pinned by where it lands**: a report a unit sends is asserted once as the status it lands on,
  through the real `AgentStatusReports` over a real `StateService`; a mocked door pins only that it was called.
- **A reader of a vendor CLI's screen is pinned on a screen captured from the installed version**, never a hand-written
  string: `working` kept "esc to interrupt" after Claude 2.1.308 dropped it (`docs/cases.md` I2).
- Why: an edited test is the cheapest wrong fix, and a bug comes back through it
  ([Approved Scenarios](https://lexler.github.io/augmented-coding-patterns/patterns/approved-scenarios/)).

### One constructor, the one production calls

- **Every constructor in `main` is called from `main`** (`ProductionConstructorTest`). Exempt: a record's canonical
  constructor, the only constructor of a Spring bean, a private one.
- A test **mocks a collaborator, never a constructor**: a clock, a pool, a path, a limit or a host fact that varies
  is a collaborator, supplied by `RuntimeWiring`, `OrchestratorPaths` or `port/Host`; a limit that never varies
  is a constant, and the test writes the volume that reaches it.
- Why: a second constructor builds in tests a class production never builds, and it sidesteps `MockCeilingTest`.

### Bare binary names, and a concurrent unit suite

- An external binary is configured by **bare name**; `adapter/Executables` resolves it against PATH, install
  directories (Homebrew included), per-user script dirs, then **inside application bundles**. An absolute
  default fails every task on Linux; `editor-command` defaults to bare `idea`.
- **Do not weaken the resolver**: an IDE needs the bundle step (`/Applications/<App>.app/Contents/MacOS/<name>`).
  The agent CLI is **not** resolved: it runs under the human's PATH in its tmux window.
- `editor-command` / `editor-diff-command` are **lists**: only the launcher is resolved, the arguments stay the
  human's; a launcher found nowhere fails naming the config **key**.
- JUnit parallel, methods **and** classes: no `@BeforeAll`, no mutable statics, every file under a `@TempDir`;
  anything competing for a **machine-wide** resource declares it (`@ResourceLock("loopback-ports")` +
  `@Execution(SAME_THREAD)`). Only this suite is parallel; it runs in English and UTC, as production does.
- **A test that asserts on a log line takes `@ResourceLock(Resources.GLOBAL)`**: else its appender captures a
  concurrent test's events and the `Logger` cast races SLF4J's start-up.
- **A flaky test is a defect, fixed when first seen**: a red a rerun turns green is never waved through. Its cause is
  found before the next commit (a race gets a fix and a `docs/cases.md` row); never a longer wait, a retry or `@Disabled`.

### The board is tested in a browser

- `boardTest` drives the real page, on a random port, in headless Chromium. Only it
  proves the grid's order, a card's buttons, the SSE repaint and the palette's verdict.
- **Run it after any change to `static/`**, asserting through the **server** (seed `StateService`, stub a
  command), never by evaluating page JS. Its `@MockitoBean`s stub whole-app seams, never a class's
  collaborators: `CommandService`, `TaskLauncher`, `NaturalLanguageDispatch`, `AgentSessions`, `EditorDriver`,
  a `ReviewRepliesReport` spy; e2e's stub only ports.
- Browser libraries are one list (`scripts/linux-test-deps.sh`); geometry is an element in a sized viewport, never a screenshot.

### The e2e matrix and the model evals

- `e2eTest` runs the flow per `TaskFlowCase` under `orchestrator.agent.cli=stub` (`StubAgentRuntime`, GUI drivers doubled), asserting an exact end
  state. Widening coverage is a **row** in `TaskFlowCase.matrix()`; an uncovered one is named there, with why.
  It asserts the **sentence** a flow returns: run it before pushing a reword.
- Two matrices: `TaskFlowCase` × `TaskFlowMatrixTest` is CREATE → TEARDOWN across the viewer combinations; `ReviewRoundCase` ×
  `ReviewAndDeployFlowTest` is everything between (ship, a round, deploy, revert, resume) on **one** combination. Its verbs go through
  the board's HTTP endpoints, the agent reports over `POST /mcp`: origins (`board` vs `mcp`) are asserted end to end.
- `promptEval` puts one operator phrasing per row (`CommandMappingCase`) through the real assistant: run it on a change to the mapping prompt,
  a `TaskAction`'s hint, the task-list shape or the model; `-PevalParallelism` (6) rows at once.
- `roleEval -Prole=master` hands the real session a round of known verdict (`MasterCase`), judged by `master-brief.md.dist`, asserting what
  the read gate heard. Run it when either or a model changes; a defect: `master-lesson`. `-PevalShards` 3 JVMs.
- `roleEval -Prole=<checker>` hands the real reader a deploy of known answer (`roles/<id>/eval/*.yml`); `-ProleBrief`, `-ProleModel`. Bar (estimate):
  false met 0 per case, false not-met <= 5%, accuracy >= 90%, report and ending right; a QA step stays only if cutting it loses more than it costs.
- Eval cases hold on `-PevalRuns` (3) / `-PevalPasses` (majority), each recording seconds, cost and stalls in `eval-stats.jsonl`.
- `roleEval -Prole=master` reads the brief's `context` step; `SessionBriefContractTest` holds a relayed step to what it grants.
- `standTest`: real sessions on invariants (`-Pmodelfree`: no tokens); `StandSlots` caps runs at 6; kicks jobs per second; `build/stand/failed`.
- A live stand run records its model moves (`recording.jsonl`); `ReplayStandTest` replays one, no model, a turn it never answered a divergence.

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
- **Only the hermetic suite is cached**: three others prove the **machine**, three a model (`cacheIf` / `upToDateWhen` false).
