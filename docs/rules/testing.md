# Testing etiquette

[← AGENTS.md](../../AGENTS.md)

## Testing etiquette

### Leave no trace

Smoke tests pass `--orchestrator.open-warp-window=false` (otherwise every run opens a Warp window that stays
behind), use a throwaway tmux session plus `ORCHESTRATOR_ROOT`, and kill the session and remove worktrees and
branches afterwards.

### No absolute macOS paths in defaults

An external binary is configured by **bare name** and resolved by `adapter/Executables`: PATH, then the known
install directories (Homebrew included, because a GUI-launched process has neither prefix on PATH), then the
per-user script directories, then **inside application bundles**.

`tmux-command` used to default to `/opt/homebrew/bin/tmux`, which made every task on Linux fail at "Failed to
start command". The agent CLI is deliberately **not** resolved: it runs inside the agent's tmux window under the
human's own PATH, and the string is what they read on screen.

`editor-command` / `editor-diff-command` are **lists**, so only the launcher is resolved and the arguments stay
the human's. A launcher nowhere to be found fails with the config **key** to set, not with the binary they never
chose.

**The bundle step is what makes the rule applicable to a desktop app at all**: an IDE's launcher lives in
`/Applications/<App>.app/Contents/MacOS/<name>` and lands in no bin directory, so defaulting `editor-command` to
`idea` without it broke `ide` on the owner's machine within the hour. **Do not weaken the resolver.**

### The suites

| suite | command | needs |
|-------|---------|-------|
| unit | `./gradlew test` | nothing — the fast hermetic gate |
| e2e matrix | `./gradlew e2eTest` | git + tmux (source set `src/e2e/java`, **not** in `test`/`check`) |
| board | `./gradlew boardTest` | Playwright's own Chromium (source set `src/boardTest/java`, not in `check`) |
| Linux drivers | `./gradlew linuxDriverTest` | Linux + binaries + a display (source set `src/linuxTest/java`, gated on `JAGT_IN_CONTAINER`) |
| console layout | `scripts/dashboard-layout-smoke.sh` | tmux + a built jar |
| console repaint | `scripts/tui-push-repaint-smoke.sh` | tmux + a built jar |

**Every fixed bug gets a regression unit test** (`sob-ai:unit-testing` rules), verified RED by actually
reverting the fix and running the test.

### The unit suite runs concurrently

JUnit parallel, methods **and** classes, which the self-contained style already allowed: no `@BeforeAll`, no
mutable statics, every file under a `@TempDir`.

A new test must keep that, and anything competing for a **machine-wide** resource declares it — the two that
pick a loopback port carry `@ResourceLock("loopback-ports")` + `@Execution(SAME_THREAD)`, because a port freed
to be probed is a port another thread can take first.

Only this suite: `e2eTest` shares branches and tmux sessions between rows, and `boardTest` seeds one
application's state.

### The board is tested in a browser

`boardTest` boots the app on a random port and drives the real page in Playwright's own headless Chromium. The
page's logic — the grid's order, the filter, which buttons a card offers, what a click POSTs, the SSE repaint,
the ⌘K palette's client-side verdict — runs nowhere else and was hand-checked until 2026-08-17.

Three write paths are `@MockitoBean`s because a real one would act on the developer's machine:
`CommandService`, `TaskLauncher`, `NaturalLanguageDispatch`.

The browser is Playwright's, never the machine's, so a Mac and a runner drive the same build; its shared
libraries are in `scripts/linux-test-deps.sh` — the **one** list, not a second one.

**Run it after any change to `static/`**, and assert through the **server** (seed `StateService`, stub a
command), never by evaluating JS in the page.

### The e2e matrix

`e2eTest` runs the flow once per `TaskFlowCase` with `orchestrator.agent=stub` (`StubAgentRuntime` — the one
non-deterministic participant replaced; every GUI driver is a Mockito double) and asserts an exact end state.

Two rules it lives by: widening coverage is adding a **row** to `TaskFlowCase.matrix()`, and a combination that
is **not** covered is named there with the reason — a silent gap reads as coverage.

Cleanup kills tmux sessions **by prefix**, because `tab-per-task` creates `<session>-<taskId>` ones the
configured name alone would leave behind.

It also asserts the **sentence** a flow returns, and `./gradlew test` cannot see it: reword a message and CI is
the first thing that notices, so run `e2eTest` before pushing one.

> [!WARNING]
> Row 1 leaves the branch behind when it fails, so rows 2–4 then fail with "branch already exists". Fix the
> **first** row and re-run before reading the rest as four bugs.

Two matrices, on purpose:

- `TaskFlowCase` × `TaskFlowMatrixTest` — CREATE → TEARDOWN across the viewer combinations.
- `ReviewRoundCase` × `ReviewAndDeployFlowTest` — everything between (ship, a round, deploy, revert, resume) on
  **one** combination, because a review round does not vary with how terminals are arranged. There the verbs go
  through the board's own HTTP endpoints and the agent reports over `POST /mcp` with its worktree header, so
  origins (`board` vs `mcp`) are asserted end to end and a surface cannot drift from the core. Its one double is
  `MasterAssistant`: the round and the request a resume adopts are stubbed on it, and the agent's own half of a
  ship — commit, push, request — is driven by the test, because that is what jagt asks of an agent rather than
  doing itself.

### Linux is testable from a Mac

`scripts/linux-suite.sh` runs `test` + `e2eTest` + `linuxDriverTest` inside a container
(`docker/linux-suite.Dockerfile`).

`linuxDriverTest` is the only place the Linux drivers meet real binaries: the notifier's message is asserted off
the session bus via `dbus-monitor`, kitty is driven under Xvfb.

Anything a container cannot host — IntelliJ, the AppleScript raise, the Warp URI scheme, the real `claude` —
stays **named as uncovered** rather than faked. Two Linux behaviours are on that list **permanently** (decided
2026-08-18, not a gap waiting to close): the viewer being raised above other applications, and closing the
viewer. Both need a window manager with a human in front of it, so the `@Disabled` test in
`LinuxKittyTerminalDriverLinuxTest` documents the lead and no pipeline pretends to cover them.

### One set of steps for every host

`.github/workflows/ci.yml` and `.gitlab-ci.yml` run the same suites by calling the **same scripts**
(`scripts/linux-test-deps.sh` = the package list, `scripts/with-linux-desktop.sh` = Xvfb + session bus +
notification daemon, then the smoke scripts).

**A step that exists in one pipeline only, or a CI-only code path, is a bug**: green in CI and green on a laptop
must mean the same thing. Neither pipeline needs Docker — the container image is for macOS developers and
installs from that same deps script. `linuxDriverTest` is gated on **capability** (Linux + the binaries + a
DISPLAY), never on "which harness am I in".

**The build cache is for the hermetic suite only.** What `e2eTest` / `boardTest` / `linuxDriverTest` prove is
the **machine**, and no machine state is in a cache key, so all three opt out (`cacheIf` / `upToDateWhen`
false) — a restored result comes back green with nothing having run, on a fresh worktree and in a pipeline that
caches `~/.gradle` alike.

### Every new install requirement

Must be documented in `docs/installation.md` — **never install things silently.**
