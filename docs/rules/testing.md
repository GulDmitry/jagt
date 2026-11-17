# Testing etiquette

[← AGENTS.md](../../AGENTS.md)

`./gradlew test` is the hermetic gate (AGENTS.md). Every other suite lives in `src/<name>/java`, none in `check`: [the suites](testing-suites.md).
**Every `test` ends in `pitestDiff`**: mutants on the `main` lines changed against `HEAD`, each survivor judged before the commit
([0125](../decisions/0125-mutants-are-judged-on-the-diff-at-each-commit.md), [0127](../decisions/0127-mutants-run-with-every-test.md),
[0151](../decisions/0151-mutants-are-judged-locally-only.md)). **`test` fails a module under 0.80 line coverage (JaCoCo)**
([0181](../decisions/0181-every-module-keeps-80-percent-line-coverage.md)). **A suite ends within `suiteCeilingMinutes`, a probe in
`-PrunMinutes`** ([0139](../decisions/0139-every-suite-ends-within-one-ceiling.md)).
**The root's `:test` is the rule tests, run whole**. **A filtered run names its project**
(`:<module>:test --tests '<Name>'`; a bare `test --tests` fails on no match).
**A test is held stricter than main** ([0162](../decisions/0162-a-check-holds-for-all-code.md)): main changes daily, its tests are what stays.
A fixed bug's commit carries `Defect-family: <A–M>`: A text vs. text. B state overwritten. C turn collision. D model overreach. E missing ending.
F unbounded loop. G prose routing. H a guard. I vendor CLI behaviour. J git and worktrees. K the board. L the tests themselves. M anything else.
**A new test's run is predicted first**, outcome and message; a surprise is a stop to understand, never a blind retry.
**Every new install requirement goes in `docs/installation.md`**. **Leave no trace** (AGENTS.md "Tests"): a suite uses a throwaway tmux session plus
`ORCHESTRATOR_ROOT`, then removes the worktrees and branches; every `Test` task runs on a tmux server of its own (`TMUX_TMPDIR`, killed after the
task), so no test reaches the human's. Shared test code (AGENTS.md "Tests"): `TextBudgetTest.mainSources` is a scanner, `ScriptedProcesses` a fake.
A test builder ([0163](../decisions/0163-a-test-shares-a-builder-a-fake-or-a-scanner.md)) is a record with `@With`, `<thing>()` setting fixed valid
defaults and `build()` calling production's constructor or builder; it holds no branch, loop, mock, assertion, clock or random value; one of outside
state (a git repo) may write. A class or member held in an ArchUnit freeze store (`src/test/resources/archunit/`) is debt to shed, never an example
to copy. A unit test is `<MainClass>[<Aspect>]Test` in its package ([0164](../decisions/0164-a-unit-test-is-named-after-its-main-class.md)).
`StateServiceCompatibilityTest` reads each `state-<minor>.json` in `state-compatibility/`; a minor without one fails; a release adds its own.

### Every test pins a business case

- A test pins what a spec scenario, a ticket or a contract promises: a serialised answer's shape is one.
- Code that only forwards or holds — a getter, a controller calling a service — gets no test; the business case through it covers it.
- **A move is pinned by where it lands**: a report a unit sends is asserted once as the status it lands on,
  through the real `AgentStatusReports` over a real `StateService`; a mocked door pins only that it was called.
- **A reader of a vendor CLI's screen is pinned on a screen captured from the installed version**, never a hand-written
  string: `working` kept "esc to interrupt" after Claude 2.1.308 dropped it.
- **A test reaches an assertion** (`EveryTestAssertsTest`): `assert*`, `verify*`, `await*`, `check*`, `andExpect*`, here or in a sibling it calls.
- Why: an edited test is the cheapest wrong fix ([pattern](https://lexler.github.io/augmented-coding-patterns/patterns/approved-scenarios/)).

### One constructor, the one production calls

- **`ProductionConstructorTest`** exempts a record's canonical constructor, a bean's or a `spring.factories` type's only one, a private one.
- A test **mocks a collaborator, never a constructor**: a clock, a pool, a path, a limit or a host fact that varies is a collaborator, supplied by
  `RuntimeWiring`, `OrchestratorPaths` or `port/host/Host`; a limit that never varies is a constant, and the test writes the volume that reaches it.
- Why: a second constructor builds in tests a class production never builds, and it sidesteps `MockCeilingTest`.

### Bare binary names, and a concurrent unit suite

- A bare binary name (AGENTS.md): `adapter/Executables` resolves it against PATH, install directories (Homebrew included), per-user
  script dirs, then **inside application bundles**. An absolute default fails every task on Linux; `editor-command` defaults to bare `idea`.
- **Do not weaken the resolver**: an IDE needs the bundle step (`/Applications/<App>.app/Contents/MacOS/<name>`).
  The agent CLI is **not** resolved: it runs under the human's PATH in its tmux window.
- `editor-command` / `editor-diff-command` are **lists**: only the launcher is resolved, arguments stay the human's; a missing one names the **key**.
- JUnit parallel, methods **and** classes, every test isolated by construction: no `@BeforeAll`, no mutable statics,
  every file under a `@TempDir`, a port bound to 0, a log read only through its own test's capture.
- **No `@ResourceLock`, `@Isolated` or `@Execution(SAME_THREAD)`**: what tests share is faked or stubbed, and a test that
  cannot run in parallel is rewritten or deleted. An extraordinary reason, written at the site, is the only exception.
- **A test boots no application**, the slowest test: a unit, then `ApplicationContextRunner`, then a slice; a `@SpringBootTest` only
  when none can pin its case, the reason at its site, on the one configuration Spring caches ([ceilings](testing-suites.md#context-ceilings)).
- **A flaky test is a defect, fixed when first seen**: a red a rerun turns green is never waved through. Its cause is
  found before the next commit (a race gets a fix); never a longer wait, a retry or `@Disabled`.

### The board is tested in a browser

- `boardTest` drives `board/` on the real backend in headless Chromium (libraries: `scripts/linux-test-deps.sh`): grid, buttons, repaint.
- Its own track, `cd board`: `npm run lint` (Prettier, ESLint, tsc, jscpd), `npm test` (node --test, c8), `npm run mutate` (Stryker); CI job `board`.
- Asserting through the **server** (seed `StateService`, stub a command), never by evaluating page JS. Its `@MockitoBean`s stub
  whole-app seams, never a class's collaborators: `CommandService`, `TaskLauncher`, `NaturalLanguageDispatch`, `AgentSessions`, `EditorDriver`,
  a `ReviewRepliesReport` spy; e2e's stub only ports. Geometry is an element in a sized viewport, never a screenshot.

### The e2e matrix and the model evals

- `e2eTest` runs the flow per `TaskFlowCase` under `orchestrator.agent.cli=stub` (`StubAgentRuntime`, GUI drivers doubled), asserting an exact end
  state. Widening coverage is a **row** in `TaskFlowCase.matrix()`; an uncovered one is named there, with why.
  It asserts the **sentence** a flow returns: run it before pushing a reword.
- Two matrices: `TaskFlowCase` × `TaskFlowMatrixTest` is CREATE → TEARDOWN across the viewer combinations; `ReviewRoundCase` ×
  `ReviewAndDeployFlowTest` is everything between (ship, a round, deploy, revert, resume) on **one** combination. Its verbs go through
  the board's HTTP endpoints, the agent reports over `POST /mcp`: origins (`board` vs `mcp`) are asserted end to end.
- `promptEval` puts one operator phrasing per row (`CommandMappingCase`) through the real assistant: run it on a change to the mapping prompt,
  a `TaskAction`'s hint, the task-list shape or the model; `-PevalParallelism` (6) rows at once.
- `roleEval -Prole=master` hands the real session a known-verdict round (`roles/master/eval/*.yml`) judged by `roles/master/brief.md.dist`,
  asserting what the read gate heard. Run it when either or a model changes; a defect: `master-lesson`. `-PevalShards` 6 JVMs.
- `roleEval -Prole=<checker>` hands the real reader a deploy of known answer (`roles/<id>/eval/*.yml`); `-ProleBrief`, `-ProleModel`. Bar (estimate):
  false met 0 per case, false not-met <= 5%, accuracy >= 90%, report and ending right; an acceptance step stays only if cutting it
  loses more than it costs. Cases hold on `-PevalRuns` (3) / `-PevalPasses` (majority), recording seconds, cost and stalls in `eval-stats.jsonl`.
- `roleEval -Prole=triage` hands the stop reader a stop of known who, lift and family (`roles/triage/eval/*.yml`), one JVM. Bar (estimate): a lift
  advised safe where unsafe 0, who right on >= 80% of answered runs; the family is recorded, not asserted.
- `roleEval -Prole=master` reads the brief's `context` step; `SessionBriefContractTest` holds a relayed step to what it grants.
- `standTest`: real sessions on invariants, in jagt's container (docker; skipped naming it if absent); `-Plocal` the host; `-Pmodelfree` no tokens.
- `StandSlots` caps runs at 6, kicks jobs per second; a run's `recording.jsonl` (model moves) and `build/stand/failed` reach the host at teardown.
- `ReplayStandTest` replays a recording, no model: a turn it never answered is a divergence.

### Linux, and one set of steps per host

- `scripts/linux-suite.sh` runs `test` + `e2eTest` + `boardTest` + `linuxDriverTest` in a container (`docker/linux-suite.Dockerfile`), the only place
  the Linux drivers meet real binaries: the notifier asserted off the session bus with `dbus-monitor`, kitty under Xvfb.
- What a container cannot host — IntelliJ, the AppleScript raise, the real `claude` — stays **named as
  uncovered**, never faked; raising and closing the viewer stay `@Disabled` (`LinuxKittyTerminalDriverLinuxTest`).
- `.github/workflows/ci.yml` and `.gitlab-ci.yml` run the same suites through the **same scripts** (`scripts/linux-test-deps.sh` the package list,
  `scripts/with-linux-desktop.sh` Xvfb + session bus + notification daemon). **A step in one pipeline only, or a CI-only code path, is a bug.**
  `linuxDriverTest` is gated on **capability**, never on the harness. The `image` job (`scripts/container-suite.sh`): the `Dockerfile` by compose,
  a stub project, a password login, `BoardContainerTest` (skipped unless `JAGT_BOARD_URL` is set), `containerTest`; release publishes it.
- **Only the hermetic suite is cached**: four others prove the **machine**, three a model (`cacheIf` / `upToDateWhen` false).
