# jagt as a desktop application — research, 2026-11-08
Is a desktop app worth building, and if not, what instead? Distribution and self-update are the main point.
Builds on [desktop-host.md](desktop-host.md) §3D and [0117](../decisions/0117-the-board-installs-as-an-app.md); §1.5 says what it overrules.
`[sN]` points to [Sources](desktop-app-sources.md), every one checked 2026-11-08; **(local)** is a measurement on this machine; **(est)** an estimate.

## 1. TL;DR
1. **No desktop shell now.** None of the five ways to build one meets the owner's bar (§2); each miss is jagt's own, not the
   packaging's: the first run, the session host on Windows, a restart story for self-update.
2. **The baseline fails first.** The release jar cannot start outside a clone (`OrchestratorPaths.findRoot`), and a clone's first run
   is seven files and up to twelve refusals (§4). Whatever the shell, this is the work: tasks 1–2 in §8.
3. **One channel for all three OS: the native installer.** One script per shell from the release's fixed URL [s57], `R` being
   `https://github.com/GulDmitry/jagt/releases/latest/download`: `curl -fsSL R/install.sh | sh` on macOS, Linux and WSL,
   `irm R/install.ps1 | iex` on Windows. It installs the `jagt` command, the jar and a pinned Temurin 25 JRE under a per-user
   directory; jagt updates itself hourly from the same release, runtime included (§6.4). It is how Claude Code, Codex, Bun, Deno, uv and
   rustup ship today (§6.9). No tap, cask or store: a manager install updates by the manager, never by itself [s28]. First run: `jagt`.
4. **Windows gets the same line now.** `install.ps1` installs into WSL2 until the native session host lands (§7); the board reaches the
   Windows browser on `localhost` [s55]. The same script installs natively once that host exists. No Windows build runs before it can run a session.
5. **The window stays the installed PWA** (0117), overruling desktop-host §3D's `chrome --app`; 0117's "weigh Tauri against jpackage"
   becomes "Conveyor, with jpackage inside", and only if a signed app bundle is ever required (a store, MDM): it is no second channel (§5).

## 2. The bar (owner, 2026-11-08)
| condition | met today | what blocks it | fixed by |
|---|---|---|---|
| one way to install, on Linux, macOS and Windows alike | no: a jar for two OS, inside a clone | Windows: no `SessionHost` but tmux, no kitty build [s26][s27], `PlatformCheck` knows `macos`/`linux` only | task 3 for the line; §7 spike, then a host |
| 100 % self-updating, downloads the latest itself | no: `git pull` + build, or a jar by hand | no updater, no checksum on the release, no restart path for a JVM that must replace its own jar | task 3 |
| configuration and tool detection work with no issues on each OS | no: §4 | the setup is a numbered refusal list; `platform` is declared, not detected; kitty, the editor and the agent CLI fail at first use | tasks 1–2 |

Against the bar, today's way is not better, only the one that works, for a developer with a clone; no packaging option changes a row (§5).

## 3. What "jagt as a desktop app" means
| part | today | in a desktop app | note |
|---|---|---|---|
| the process | `java -jar` in a terminal, a JVM you installed | the jar plus a jlink runtime, owned by a launcher | 24.4 MB jar (local); jlink 12 modules 53 MB, ~80 MB image (local, est) |
| the window | browser tab, or the installed PWA (0117) | the same PWA; never a webview serving `app://` or `tauri://`: its Origin breaks `LoopbackFilter`, every write is lost (0117) | a webview adds a runtime and takes nothing off the board |
| start, stop, restart | you | a tray or menu-bar item, a login item | `java.awt.SystemTray` names Windows, GNOME, KDE; macOS is unstated, `isSupported()` decides [s21] |
| first run | README steps 1–5, refusals | a setup page on the board | §4 |
| configuration | `jagt.yml`, the `project` verb writes entries | the same file, written by the setup page | "one copied file and one command" holds |
| tool detection | `ToolchainCheck`, `PlatformCheck`, `Executables` | the same probes, shown with the install line per OS | kitty and the editor are probed only when pressed |
| self-update | none | a job that downloads, verifies, swaps, restarts | §6.4 |
| logs | `jagt-backend.log` beside the working directory | a per-user log directory, set by the launcher | §6.6 |

## 4. Today's first run — only the release jar, a clean machine; each line names the file that stops them
| # | step | what stops them | where |
|---|---|---|---|
| 1 | `java -jar jagt-<v>.jar` | no JVM: the OS says so; jagt needs Java 25 | `jagt.conventions.gradle`, `installation.md` |
| 2 | the jar starts | "Cannot locate orchestrator root: none of [jagt.yml.dist, mcp_client.js] found … Set ORCHESTRATOR_ROOT" | `OrchestratorPaths.findRoot` |
| 3 | they clone instead (README step 2) | `git clone`, `cp jagt.yml.dist jagt.yml`; "Missing jagt.yml — copy jagt.yml.dist" otherwise | `ConfigService.section` |
| 4 | they edit `jagt.yml` | `projects` has no default: "defines no projects"; a path, `baseBranch`, a fetched remote or `remote: none` + `codeHost: none` | `ConfigCheck`, `RemoteCheck`, `jagt.yml.dist` |
| 5 | on Linux | `platform` defaults to `macos` and is refused: "jagt drives macOS and Linux, and this machine …" | `PlatformCheck` |
| 6 | start again | git, tmux, pgrep each refused by name; the list is printed whole | `ToolchainCheck` |
| 7 | `agent.cli: codex` or `opencode` | node and `mcp_client.js`, or the `opencode` command, refused | `CodexAgentRuntime`, `OpenCodeCheck` |
| 8 | `sandbox.enabled: true` on Linux | bubblewrap and socat refused | `ClaudeSandboxCheck` |
| 9 | port 8290 held | "port 8290 is in use, most likely another jagt" | `StartupFailure` |
| 10 | the board opens, New task | Claude Code missing or its headless call has no MCP: the first read fails, not the start | `AgentCliFailureAnalyzer`, README step 1 |
| 11 | `focus` | no kitty: "Focus raises no window"; on macOS a non-UTF-8 locale drops input | `troubleshooting.md`, `installation.md` |
| 12 | `ide` | `editorCommand` defaults to `idea`; absent, the launch fails when pressed | `jagt.yml.dist` |
| 13 | the first session builds | `copyGlobs` too narrow: the worktree lacks the untracked files the build reads | README step 2 |

Tasks 1–2 (§8) answer 1–8 and 10–13: 1 — a bundled runtime or the install line for Java; 2–3 — classpath resources, `root` a per-user
directory (reopens 0021); 4, 13 — the setup page's `project` verb, remote probe, `copyGlobs` hint; 5 — platform read, the key stays as
override; 6–8, 11–12 — tools probed with an install line per OS; 10 — README step 1's MCP check. 9 is unchanged. Still the human's: the
project path, the Master's mode, logging in to the agent CLI.

## 5. Options scored against the bar
| way | one way, three OS | self-update | JVM for the user | detection | cost: signing · CI · licence | the user types | verdict |
|---|---|---|---|---|---|---|---|
| the clone (today) | 2 OS, developers only | `git pull` + Gradle build | a JDK you install | refusal list | none · one Linux job · — | `git clone`, `cp`, `./gradlew` | stays for developing jagt only |
| release jar alone (today) | 2 OS, inside a clone | none | no | refusal list | none · one job · — | `java -jar` | fails §4 step 2 |
| installed PWA (0117), the window of every row | all 3 OS | with the jar | — | — | none · none · free | nothing | prompt on 127.0.0.1 and localhost unverified: read it through CDP `Page.getInstallabilityErrors` on the debug Chrome (task 2, step 0) |
| **native installer**: `install.sh` + `install.ps1` on the release, `jagt` launcher, `versions/`, own updater | yes: 3 OS, WSL2 on Windows until §7 | 100 %: hourly job, runtime included (§6.4) | yes: Temurin 25 JRE pinned by the release manifest, Adoptium's fixed URL per OS and arch [s60] | task 2 | none · one Linux job for the platform-neutral jar, a Windows job testing the script, free on a public repository [s56] · — | one line per shell, then `jagt` | **first**: task 3 |
| npm `-g`: per-OS optional dependency, postinstall fetches the JRE | one command on 3 OS | itself, if the global directory is writable [s28] | no: Node 22+ first [s28], then the JRE | task 2 | none · one job · — | `npm i -g jagt` | second: one runtime to install another; npm's permission trap [s28] |
| jbang: `jbang app install <gav>` | one command, after jbang's own sh/ps install [s53] | none: reinstall by hand; jbang's own self-update unknown [s53] | yes: downloads a JDK [s53] | task 2 | none · one job · — | two lines, then `jbang app install …` | no: a tool between the user and jagt, no updater; a fat jar from a URL unverified |
| Coursier: `cs install` | native `cs` per arch; Windows directory unknown [s54] | `cs update` by hand [s54] | `cs setup` installs a JVM 21 [s54] | task 2 | none · one job · — | `cs setup`, `cs install jagt` | no: Scala's launcher, manual update |
| Homebrew tap formula + curl (the earlier pick) | 2 OS: no Homebrew on Windows | `brew upgrade` by hand [s5], or jagt runs it [s28] | brew's `openjdk` [s3] | task 2 | none · JReleaser writes the formula [s17] · free | `brew install …` | rejected by the owner: two ways, no Windows |
| homebrew-core, SDKMAN, winget | one or two OS each | by hand [s1][s18][s24] | — | — | PR review; 75 stars / 30 days [s1]; vendor onboarding [s18] | per manager | not channels: a manager each |
| JReleaser to every channel: tap, scoop, winget, sdkman, jbang, snap, flatpak, AppImage [s51] | many ways from one release | each by its manager, none by itself [s28] | per channel | task 2 | none · one job · Apache-2.0, v1.27.0 [s51] | per channel | no as a channel; its checksum and release steps may serve task 3 |
| Conveyor | one config, three native packages from one Linux runner [s8][s52] | Sparkle, MSIX in the background, apt: built in; Fedora, Arch, the tarball: none [s52] | yes: a jlink JDK per target [s52] | task 2 | Apple 99 USD/yr [s11]; Windows: a CA certificate, or Azure Artifact Signing 9.99 USD/month, individuals in the US or Canada only [s58], else self-signed and SmartScreen [s59] · one Linux runner · free for OSI licences, 45 USD/month else [s9] | a download page, an installer per OS | later, only if a signed bundle is required; a headless app's update: unknown [s52] |
| jpackage + own updater | 2 (+1 once §7) | own job (§6.4) | yes, ~80 MB [s7] | task 2 | Developer ID, notarize yourself [s36] · one runner per OS [s7] · JDK tool | an installer per OS | later, inside Conveyor |
| Tauri / Electron shell | 2 (+1) | Tauri updater, signature mandatory [s25] | still the JVM, plus a webview | task 2 | as above · per OS · MIT | an installer per OS | rejected (0117) |
| GraalVM native image | one binary per OS and arch, over the same scripts | own job | no JVM; size unknown | task 2 | as above · per OS with gcc / Xcode / MSVC [s16] · GraalVM for JDK 25, Liberica NIK [s15] | the same line | not now: a runtime variant of the installer, hints for Jackson 3 and roles untested |

## 6. Each aspect
### 6.1 Window, tray, accessibility, notifications, instances
- The window stays the browser's: the installed PWA starts at OS login since Chrome 91 on all three OS [s20]; it cannot start the JVM (0117).
- A tray from Java is an experiment, not a given: the JDK 25 javadoc names no macOS location and asks for `isSupported()` [s21]; a native
  tray comes with Conveyor's launcher only if we write one — unknown until tried.
- Accessibility: the PWA window has the browser's accessibility tree and the board's keys (`t`); a webview's is unknown. One more reason for the PWA.
- Notifications stay native (`MacNotifier`, `LibNotifyNotifier`): the PWA's Notifications API would be a second channel for one question.
- One instance per port: a second jagt is refused (`StartupFailure`); boards (tabs, PWA windows) share it over SSE; two need two roots and ports.

### 6.2 First run, configuration, tool detection
- One source stays `jagt.yml`; the setup page writes it, as the `project` verb already does. No second configuration store.
- Every probe already exists (`ToolchainCheck`, `PlatformCheck`, `Executables`, the checks in `startup/`); the page shows them with an
  install line per OS instead of refusing. Startup keeps refusing only what no page can fix: the port, an unwritable root.
- `Executables` already covers a GUI-launched process whose PATH lacks Homebrew: the launcher changes nothing there.

### 6.3 Distribution and signing
- A script-installed jar is no app bundle: Gatekeeper judges app launches, and `java -jar` from a shell shows no dialog (local). Windows:
  SmartScreen judges a downloaded executable by reputation and signature [s59]; on a JRE the script unpacks: unknown, task 3 measures it.
- A bundle needs the Apple Developer Program, 99 USD per year [s11]; a dmg a Developer ID Application certificate, a pkg also an Installer
  one [s36]; unsigned: since Sequoia only System Settings › Privacy & Security › Open Anyway [s12][s13]. Homebrew 5.0.0 deprecated
  `--no-quarantine` and disables casks failing Gatekeeper from September 2026 [s6]: an unsigned cask is no channel.
- A Windows bundle needs a CA certificate (price unknown) or Azure Artifact Signing: 9.99 USD/month for 5,000 signatures, Public Trust for
  individuals only in the US and Canada, organisations in more countries [s58]; a file without reputation gets SmartScreen's warning [s59].
- The signing identity would be the project's CI secret, never a token jagt holds at runtime: 0020 stands.
- Linux: the script covers every distribution with `curl` and `tar`; deb/rpm come from jpackage [s7] or Conveyor's apt repository [s52]. Windows: §7.

### 6.4 Self-update and restart safety
- Layout as Claude Code's native install [s28]: a launcher on PATH, `versions/<v>/` beside it, the launcher pointing at one. Windows locks a
  running executable [s28], so the new version lands beside the old and the launcher switches on restart; nothing is overwritten in place.
- Check: `releases/latest` once an hour; unauthenticated GitHub allows 60 requests per hour per IP [s19].
- Download to a **new file name**, never the running inode: a rewritten jar kills the JVM on its next class load (`RunningJarWatch`, `stageJar`).
- Verify: `ci.yml` attaches the jar only; a manifest with a sha256 per asset is the minimum, a signature the goal (Claude Code signs its [s28]).
- The runtime is in the manifest: the Temurin build and its sha256 per OS and arch, fetched from Adoptium's fixed URL, which today resolves
  25 to `jdk-25.0.5+8` on GitHub [s60], or a jlink image per OS built in CI (five assets, ~53 MB each, local est): task 3 measures which.
  Installer and updater read the same line, so a runtime bump is a release like any other.
- Dry-run the new jar's startup checks before the swap: `ClaudeMovedKeysCheck` and `ConfigCheck` refuse renamed keys, and an update that
  leaves jagt refusing to start is worse than no update. A key rename reads the old key for one version.
- Restart: a JVM cannot exec itself; the launcher restarts it on a named exit code, or the service manager does (`keep_alive`, `KeepAlive`).
- What survives: tmux sessions are detached and outlive jagt by design (`runtime` spec; `ProcessRunner` stops only awaited commands);
  the board reconnects over SSE; `state.json` is written atomically with a `.bak`. A reboot costs the windows, `focus` re-enters (README).
- Precedents agree (§6.9); update4j is archived since 2024-03-18 [s23].

### 6.5 `state.json` across versions
- Reads migrate on the fly: `StoredTask.Unmigrated` carries `silentSince`, `loop`, `qa`, `pipelineUnread`, `conflict`, the report marker.
- There is no schema version field, and only `ReportMarkerMigrationTest` pins a migration; no test reads a previous release's file.
- A self-updating jagt needs that fixture per released minor (task 4), and a field dropped only one version after it stopped being written.

### 6.6 Start at login, logs
- macOS: a LaunchAgent in the user's `Library/LaunchAgents`, `KeepAlive`, `StandardOutPath` [s33], written by the launcher (a formula's
  `service do` would have [s5]; the tap is gone). Linux: a unit under `~/.config/systemd/user/` [s34].
- The log moves with the launcher: `LOG_FILE` is already the switch (`logback-spring.xml`); a per-user log directory replaces the working directory.
- Login item: default off, the page offers it (precedents §6.9).

### 6.7 Security
- Unchanged by any shell: `127.0.0.1` only, no password, `LoopbackFilter` on Origin and Host, `BoardWriteFilter` on every write. A local
  process can already forge an Origin with curl; a login item only lengthens the time the port is open.
- The updater is a new trust root: it executes what it downloads, so the checksum or signature check of §6.4 is not optional.

### 6.8 CI and release
- Today: one Linux job builds the jar, `release` attaches it to the tag, two minors are kept. The installer keeps that shape: the jar is
  platform-neutral, the scripts are text, the runtime is Adoptium's; one Windows job tests `install.ps1`. Standard runners are free on a
  public repository; private: Linux 0.006, Windows 0.010, macOS 0.062 USD per minute [s56]. Runners per option: §5.
- JReleaser's jlink assembler builds an image per platform from JDKs you install; its jpackage assembler is per OS [s51].

### 6.9 How comparable tools ship today
Twenty tools, one row each, in [desktop-app-tools.md](desktop-app-tools.md). The pattern: a developer tool ships one script per shell over
its own release feed and updates itself; a GUI app ships an installer per OS and updates itself; a manager install updates by the manager [s28].

## 7. Windows
- Blocked in code, not in packaging: `SessionHost` has one driver, tmux; `TerminalDriver` has kitty (two OS) and Ghostty (macOS);
  `PlatformCheck` accepts `macos` and `linux`; notifications go through osascript, terminal-notifier or notify-send; keep-awake through
  caffeinate or systemd-inhibit; liveness through pgrep, lsof, pkill; `Executables` searches Unix directories; jagt's git hooks run under `sh`.
- tmux ships for Linux and macOS only [s26]; MSYS2 packages tmux 3.8 in its POSIX layer [s35], its pty behaviour under a Windows agent CLI
  unknown; kitty has no Windows build [s27]. Claude Code runs natively on Windows, PowerShell or Git Bash [s28]: the agent is not the blocker.
- Until the host: `install.ps1` runs `install.sh` in the default WSL2 distribution; WSL's default NAT mode serves a Linux port to a Windows
  browser on `localhost` [s55]; the agent CLI runs in WSL [s28]; tmux and the board work; `focus` raises no window (kitty [s27]), the page says so.
- A native host: a `SessionHost` over ConPTY with detach and re-attach (the hard part: tmux's revive after a jagt restart), a Windows
  Terminal driver, a toast notifier, a keep-awake, path handling, a Windows CI runner. Cost unknown until the spike (task 5); weeks, not days (est).
  When it lands, the same `install.ps1` installs natively; a WSL install keeps updating as the Linux one it is.

## 8. Recommendation and tasks — the first run, then the installer with its updater, then Windows by measurement, the image last (costs est)
| # | task | replaces | done when | cost |
|---|---|---|---|---|
| 1 | **Self-contained jar**: `jagt.yml.dist`, `mcp_client.js`, `roles/*.dist` on the classpath; `root` = `--root`, `ORCHESTRATOR_ROOT`, else a per-user data dir; reopens 0021 | the clone, 0021 | `java -jar jagt.jar --root=$(mktemp -d)` answers `/state` 200 and shows the setup page; `./gradlew test e2eTest` exit 0 | 3–5 days |
| 2 | **First-run page** on the board: platform read from the machine, every tool probed with its install line per OS, the first project via the `project` verb, the MCP check, `copyGlobs` hint; startup refuses only port and root; step 0: the PWA install prompt measured on 127.0.0.1 and localhost through CDP `Page.getInstallabilityErrors`, the result written into 0117 | README steps 2–4, the refusal list, 0117's open line | `boardTest` scenario: empty root → a task created on a stub project, exit 0 | 3–4 days |
| 3 | **Native installer + `jagt` command + self-update**: `install.sh`, `install.ps1` and a manifest (jar, Temurin build, sha256 per asset) attached to the release; per-user `versions/<v>/`, `runtime/`, a launcher on PATH that restarts on a named exit; hourly check of `releases/latest`, download beside, verify, dry-run checks, switch, exit; `install.ps1` installs into WSL2 until task 5's host | `git pull` + build, the manual jar, the tap | stand run on a fake feed v0 → v1: the tmux session and a task's state intact, board reconnected; `install.sh` on a clean Linux runner and `install.ps1` on a Windows runner reach `/state` 200; `e2eTest` exit 0 | 4–5 days |
| 4 | **State fixture per release**: the `state.json` of each released minor under test resources, read without loss; the release step adds it | nothing: closes §6.5's gap | `StateCompatibilityTest` exit 0 | 1 day |
| 5 | **Windows host spike**: tmux under MSYS2, ConPTY + Windows Terminal, WSL2-as-Linux, against the `runtime` spec's scenarios; output is a decision record | the open question, not code | the decision merged, with measurements | 2 days |
| 6 | **Conveyor image** (only if a signed bundle is required: a store, MDM): `conveyor.conf` over the jar, login item, tray if `isSupported()`, Sparkle/MSIX/apt; macOS and Linux until task 5 lands | nothing on the board: the installer stays the channel | each image starts, answers `/state`, updates from a test site; `boardTest` exit 0 | 2–3 days + 99 USD/yr + a Windows certificate + the signing decision (an earlier jpackage-only sketch: 3–5 days, 0020) |
| 7 | **Live Session tab** (only when a remote board or a viewer-less platform is asked for): tmux control mode in `adapter/tmux`, WebSocket under both filters, xterm.js vendored on demand | the 5 s capture-pane poll and the `say` line | `e2eTest` scenario typing a line and reading its echo; `boardTest`, `BoardAssetsTest`, all exit 0 | 4–6 days |
| 8 | **Changes artifact** (only after diff presses are measured above zero): read-only diff of the task branch against its base in the Artifacts tab | the refusal when `editorDiffCommand` is unset | `:surface:test` and `boardTest` exit 0 | 1–2 days |

Not a task: Electron, Tauri, a webview, GraalVM, npm `-g`, jbang, Coursier, a tap, homebrew-core, SDKMAN, winget, JReleaser as a channel (§5).

## 9. Open questions
- Where does `root` live by default: a per-user data directory, or beside the launcher? It also holds `worktrees/`, which are cut beside it today.
- Does the launcher hold the restart, or the service manager (launchd, systemd)? One of them, never both.
- Does Gatekeeper treat a JRE unpacked by `install.sh` as it treats `java -jar` from a shell (no dialog, local)? Task 3, on the macOS runner.
- Is the macOS tray reachable from Java at all? Measured by one `isSupported()` run inside a jpackage image; else Conveyor's launcher only.
- Does Tauri's smaller shell matter once the JVM is bundled anyway? Unknown: needs one measured image per option.
- Keys the project would hold: a release signing key (sha256 the minimum) and the Apple account; 0020: jagt holds none, CI secrets are the project's.

## 10. Terminal, editor, activity log — sources checked 2026-11-08 (owner's wider question: is a separate desktop surface wanted)
| question | finding |
|---|---|
| verdict | No separate app: the platform is the board plus launchers, grown by the activity log; a shell only on 0021's trigger; a terminal is embedded only when the board must reach a machine without the viewer |
| usage evidence | `interactions.jsonl` 2026-10-16..11-05, 1864 presses: focus 294, tab-artifacts 65, tab-session 6, ide 3, diff 0, show-activity 1, show-finished 1. The real terminal is wanted and costs nothing (focus); the read-only Session tab (capture-pane every 5 s, 0060) is nearly unused |
| shell options | Electron: MIT, 120.8 MB before app and JVM (0117). Tauri 2: MIT or Apache-2.0, shell 3–15 MB (secondary benchmarks, unverified), Rust toolchain, webkit2gtk-4.1 on Linux (0117), still bundles the JVM. jpackage: 12 modules, 53 MB runtime + 24 MB jar ≈ 80 MB (est), builds only on its target OS [s7]. PWA (0117): 0 MB, Chrome and Edge on all three OS, Safari on macOS; MDN counts localhost and 127.0.0.1 as secure, web.dev's install criteria list manifest fields, no service worker. Recommendation: PWA now; on 0021, jpackage with a tray opening the PWA URL; never Electron; Tauri only if a webview must own the page |
| what a shell adds | Only JVM ownership (start, stop, tray, dock icon): it replaces `java -jar` and the clone (0021), nothing on the board. It buys no OS: Windows is blocked by the session host (§7) |
| licences | Each repo's licence file: xterm.js MIT, ghostty/libghostty MIT, kitty GPL-3.0, tmux ISC, pty4j EPL-1.0. kitty as today's separate process over its socket is aggregation, no licence bind; linking it would be, and it ships no embeddable library |
| libghostty | "not yet a stable API and has not been released as a standalone, stable library" (ghostty.org/docs/about); a C-ABI with a native renderer, so only inside a native shell, never a browser page: rejected for now |
| tmux already gives | A window per session, capture-pane (Session tab), send-keys (`say`), attach from any terminal, revive after a restart, detach when the viewer closes, a named socket |
| embedding, if ever | tmux control mode (`tmux -C attach`) over stdin/stdout, no PTY library; a WebSocket behind `LoopbackFilter` and `BoardWriteFilter`; xterm.js 5.5.0 vendored (lib 289,441 B + css 5,559 B from unpkg), loaded on demand like mermaid (0054), so the 160 KB budget holds |
| why it does not pay | ttyd shipped off and never on (0002), the Session tab is unused, focus is free; it pays only from another machine or a platform without the viewer (0060's reopen line) |
| editor, diff | Built-in editor or diff not worth it: diff pressed 0 times, ide 3; the configured editor is a launcher (0047); the code host shows the diff after ship. If a diff is wanted: a read-only Changes document in the Artifacts tab, server-rendered through the `LineDiff` port, replacing the refusal when `editorDiffCommand` is unset; never an editor |
| activity log | Built (b8d5c3a52): worth it and cheapest. Before, it was split across the Overview tab (50 steps per task, who asked, why in hover), the finished report and the activity report tailing the log (40 entries). Design: widen the activity report, never a second control: every `StatusChange` of live tasks plus `finished.jsonl`, newest first, a line opens its task, filters by kind (shipped, deployed, stopped, done) and project; a per-task filter is the Overview tab's question |
| one surface | A shell hosting the jar's own page (same /api, same `Move.actions`) is a window like the PWA; a native UI with its own cards or verbs is the second surface 0002 removed (86 files, 3338 lines). If one survives: the browser board (banners `?task=`, `server.address` from another machine, the PWA, `boardTest` reach it) |
| AGENTS check | One surface (0002) untouched; a shell is no seam, a terminal view would be a `SessionHost` read; no build step holds (vendored xterm.js); no GUI automation (send-keys to a named pane is what `say` does). Design changes over the first sketch: the terminal, if ever, is the Session tab made live, not a pane beside tasks; the log is one report widened; the diff is an artifact; the shell is jpackage, not a webview |
