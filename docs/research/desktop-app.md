# jagt as a desktop application — research, 2026-11-08
Is a desktop app worth building, and if not, what instead? Distribution and self-update are the main point.
Builds on [desktop-host.md](desktop-host.md) §3D and [0117](../decisions/0117-the-board-installs-as-an-app.md); §1.5 says what it overrules.
`[sN]` points to [Sources](#sources), every one checked 2026-11-08; **(local)** is a measurement on this machine; **(est)** an estimate.

## 1. TL;DR
1. **No desktop shell now.** None of the five ways to build one meets the owner's bar (§2); each miss is jagt's own, not the
   packaging's: the first run, the session host on Windows, a restart story for self-update.
2. **The baseline fails first.** The jar `ci.yml` attaches to a release cannot start outside a clone (`OrchestratorPaths.findRoot`),
   and a clone's first run is seven files and up to twelve refusals (§4). Whatever the shell, this is the work: tasks 1–2 in §8.
3. **The channel that replaces the clone**: a Homebrew tap formula (macOS, Linux) and a curl line for machines without brew, both installing
   one `jagt` command over a self-contained jar. Download: the 24 MB jar plus `openjdk` from brew. Update: jagt's own hourly job swaps the
   jar and restarts under tmux's protection, `brew upgrade` the manual path. First run: `jagt` starts and the board opens its setup page.
   The clone exists only because the jar reads four files from disk; it stays for developing jagt, not for running it.
4. **Windows** is blocked by the session host (tmux, kitty, `platform`, the notifiers), not by packaging. WSL2 runs the Linux build
   today; a native host is a separate decision after a two-day spike (§7). No Windows executable ships before it can run a session.
5. **Later, on 0021's trigger** (a user who is not a developer): Conveyor over the same jar — free for an Apache-2.0 project, three OS
   images from one Linux runner, Sparkle/MSIX/apt updates, 99 USD/yr to Apple. Overrules desktop-host §3D in one point: the window is
   the installed PWA (0117), not `chrome --app`; 0117's "weigh Tauri against jpackage" becomes "Conveyor, with jpackage inside".

## 2. The bar (owner, 2026-11-08)
| condition | met today | what blocks it | fixed by |
|---|---|---|---|
| three executables: Linux, macOS, Windows | no: a jar for two OS, inside a clone | Windows: no `SessionHost` but tmux, no kitty build [s26][s27], `PlatformCheck` knows `macos`/`linux` only | §7 spike, then a host |
| 100 % self-updating, downloads the latest itself | no: `git pull` + build, or a jar by hand | no updater, no checksum on the release, no restart path for a JVM that must replace its own jar | task 3 |
| configuration and tool detection work with no issues on each OS | no: §4 | the setup is a numbered refusal list; `platform` is declared, not detected; kitty, the editor and the agent CLI fail at first use | tasks 1–2 |

Against the bar, today's way is not better, only the one that works, for a developer with a clone; no packaging option changes a row (§5).

## 3. What "jagt as a desktop app" means
| part | today | in a desktop app | note |
|---|---|---|---|
| the process | `java -jar` in a terminal, a JVM you installed | the jar plus a jlink runtime, owned by a launcher | 24.4 MB jar (local); jlink 12 modules 53 MB, ~80 MB image (local, est) |
| the window | browser tab, or the installed PWA (0117) | the same PWA; never a webview serving `app://` (Origin breaks `LoopbackFilter`) | a webview adds a runtime and takes nothing off the board |
| start, stop, restart | you | a tray or menu-bar item, a login item | `java.awt.SystemTray` names Windows, GNOME, KDE; macOS is unstated, `isSupported()` decides [s21] |
| first run | README steps 1–5, refusals | a setup page on the board | §4 |
| configuration | `jagt.yml`, the `project` verb writes entries | the same file, written by the setup page | "one copied file and one command" holds |
| tool detection | `ToolchainCheck`, `PlatformCheck`, `Executables` | the same probes, shown with the install line per OS | kitty and the editor are probed only when pressed |
| self-update | none | a job that downloads, verifies, swaps, restarts | §6.5 |
| logs | `jagt-backend.log` beside the working directory | a per-user log directory, set by the launcher | §6.7 |

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

What the design answers, item by item: 1 — a bundled runtime (Conveyor/jpackage) or the install line for Java in the curl/brew installer;
2–3 — the three `.dist` files and `mcp_client.js` become classpath resources and `root` a per-user data directory (reopens 0021);
4, 13 — the setup page adds the first project through the `project` verb, probes the remote and shows the `copyGlobs` hint; 5 — the
platform is read from the machine and written, the key stays for the override; 6–8, 11–12 — every tool is probed at setup with its
install line per OS, kitty and the editor included; 9 — unchanged, a second instance is refused; 10 — the MCP check of README step 1
runs from the page and shows `NONE` where it is. Still the human's: the project path, the Master's mode, logging in to the agent CLI.

## 5. Options scored
| way | three OS | self-update | detection | download, JVM inside | signing on macOS | CI | licence, price | verdict |
|---|---|---|---|---|---|---|---|---|
| the clone (today) | 2, developers only | `git pull` + Gradle build, a JDK on the machine | refusal list | the repository, no | none | — | — | stays for developing jagt only |
| release jar alone (today) | 2, inside a clone | none | refusal list | 24 MB, no | none | one job | — | fails §4 step 2 |
| installed PWA (0117), the window of every row | all 3 OS | with the jar | — | 0 MB, no | none | none | free | prompt on 127.0.0.1 and localhost unverified: read it through CDP `Page.getInstallabilityErrors` on the debug Chrome (task 2, step 0) |
| curl installer / Homebrew tap formula | 2 | `brew upgrade` by hand [s5]; jagt may run it itself as Claude Code does [s28] | task 2 | jar + `openjdk` dependency [s3] | none: no bundle | one job, JReleaser writes the formula [s17] | free | **first**, with task 3's updater |
| homebrew-core | 2 | same | task 2 | same | none | PR review | needs 75 stars / 225 if self-submitted, 30 days [s1] | not yet eligible |
| jpackage + own updater | 2 (+1 once §7) | own job (§6.5) | task 2 | dmg/pkg, deb/rpm, msi; ~80 MB, yes [s7] | Developer ID cert, notarize yourself [s36] | one runner per OS [s7] | JDK tool | **later**, inside Conveyor |
| Conveyor | 2 (+1 once §7) | Sparkle 2 on mac, MSIX, apt; built in [s8][s10] | task 2 | ~80 MB (est), yes | notarization by API key, or `sign = false` [s10] | one Linux runner builds all [s8] | proprietary; free for OSI-licensed, else $45/month [s9] | **later**, on 0021's trigger |
| Tauri / Electron shell | 2 (+1) | Tauri updater, signature mandatory [s25] | task 2 | + a webview, Rust or Node; still the JVM | same as above | per OS | MIT | rejected (0117) |
| GraalVM native image | 2 (+1) | own job | task 2 | one binary per OS/arch, no JVM; size unknown | same | per OS with gcc / Xcode / MSVC [s16] | GraalVM for JDK 25, Liberica NIK [s15] | not now: hints for Jackson 3 and roles, untested |
| SDKMAN | 2 | `sdk` by hand | task 2 | zip, no | none | vendor API after onboarding [s18] | cost unknown | not before the tap |
| winget | Windows | `winget upgrade` by hand [s24] | — | msi/exe/zip | Windows signing | PR to winget-pkgs | free | after §7 only |

## 6. Each aspect
### 6.1 Window, tray, accessibility
- The window stays the browser's: the installed PWA starts at OS login since Chrome 91 on all three OS [s20]; it cannot start the JVM (0117).
- A tray from Java is an experiment, not a given: the JDK 25 javadoc names no macOS location and asks for `isSupported()` [s21]; a native
  tray comes with Conveyor's launcher only if we write one — unknown until tried.
- Accessibility: the PWA window has the browser's accessibility tree and the board's keys (`t`); a webview's is unknown. One more reason for the PWA.

### 6.2 First run, configuration, tool detection
- One source stays `jagt.yml`; the setup page writes it, as the `project` verb already does. No second configuration store.
- Every probe already exists (`ToolchainCheck`, `PlatformCheck`, `Executables`, the checks in `startup/`); the page shows them with an
  install line per OS instead of refusing. Startup keeps refusing only what no page can fix: the port, an unwritable root.
- `Executables` already covers a GUI-launched process whose PATH lacks Homebrew: the launcher changes nothing there.

### 6.3 Notifications, instances
- Notifications stay native (`MacNotifier`, `LibNotifyNotifier`): the PWA's Notifications API would be a second channel for one question.
- One instance per port: a second jagt is refused (`StartupFailure`); boards (tabs, PWA windows) share it over SSE; two need two roots and ports.

### 6.4 Distribution and signing
- A formula or a curl-installed jar is no app bundle: Gatekeeper judges app launches, and `java -jar` from a shell shows no dialog (local).
- A bundle needs the Apple Developer Program, 99 USD per year [s11]; a dmg needs a Developer ID Application certificate, a pkg also an
  Installer one [s36]. Unsigned: since Sequoia no Control-click override, only System Settings › Privacy & Security › Open Anyway [s12][s13].
- Homebrew 5.0.0 deprecated `--no-quarantine` and disables casks failing Gatekeeper from September 2026 [s6]: an unsigned cask is no channel.
- The signing identity would be the project's CI secret, never a token jagt holds at runtime: 0020 stands.
- Linux: deb/rpm from jpackage [s7] or Conveyor's apt repository; a formula works on Linux too. Windows: §7.

### 6.5 Self-update and restart safety
- Check: `releases/latest` once an hour; unauthenticated GitHub allows 60 requests per hour per IP [s19].
- Download to a **new file name**, never the running inode: a rewritten jar kills the JVM on its next class load (`RunningJarWatch`, `stageJar`).
- Verify: `ci.yml` attaches the jar only; a sha256 beside it is the minimum, a signature the goal.
- Dry-run the new jar's startup checks before the swap: `ClaudeMovedKeysCheck` and `ConfigCheck` refuse renamed keys, and an update that
  leaves jagt refusing to start is worse than no update. A key rename reads the old key for one version.
- Restart: a JVM cannot exec itself; the launcher restarts it on a named exit code, or the service manager does (`keep_alive`, `KeepAlive`).
- What survives: tmux sessions are detached and outlive jagt by design (`runtime` spec; `ProcessRunner` stops only awaited commands);
  the board reconnects over SSE; `state.json` is written atomically with a `.bak`. A reboot costs the windows, `focus` re-enters (README).
- Precedents agree: Ollama downloads updates itself and offers "Restart to update" from the menu bar [s29]; Syncthing upgrades itself
  within 24 h from GitHub and leaves packaged builds to the package manager [s30]; Claude Code updates natively in the background and
  runs `brew`/`winget` for you only when asked [s28]; update4j is archived since 2024-03-18 [s23].

### 6.6 `state.json` across versions
- Reads migrate on the fly: `StoredTask.Unmigrated` carries `silentSince`, `loop`, `qa`, `pipelineUnread`, `conflict`, the report marker.
- There is no schema version field, and only `ReportMarkerMigrationTest` pins a migration; no test reads a previous release's file.
- A self-updating jagt needs that fixture per released minor (task 4), and a field dropped only one version after it stopped being written.

### 6.7 Start at login, logs
- macOS: a LaunchAgent in the user's `Library/LaunchAgents`, `KeepAlive`, `StandardOutPath` [s33]; `brew services` writes it from the
  formula's `service do` block, with `keep_alive` and `log_path` [s5]. Linux: a unit under `~/.config/systemd/user/` [s34].
- The log moves with the launcher: `LOG_FILE` is already the switch (`logback-spring.xml`); a per-user log directory replaces the working directory.
- Ollama registers a login item at install on macOS and Windows [s29]; Docker Desktop ships it off by default [s31]. Default off, the page offers it.

### 6.8 Security
- Unchanged by any shell: `127.0.0.1` only, no password, `LoopbackFilter` on Origin and Host, `BoardWriteFilter` on every write. A local
  process can already forge an Origin with curl; a login item only lengthens the time the port is open.
- A webview serving the page from its own scheme sends a foreign Origin and loses every write (0117): the window loads the loopback URL or nothing.
- The updater is a new trust root: it executes what it downloads, so the checksum or signature check of §6.5 is not optional.

### 6.9 CI and release
- Today: one Linux job builds the jar, `release` attaches it to the tag, two minors are kept. Conveyor keeps that shape: one Linux runner,
  three images, signing and notarization from there [s8]. jpackage needs a runner per OS [s7]; GraalVM too, each with its toolchain [s16].
- JReleaser (Apache-2.0, v1.27.0) writes the tap formula, the SDKMAN and winget entries from the release [s17]; its jpackage assembler is per OS.

### 6.10 Comparable local-server tools with a web UI
| tool | ships as | updates | login item | source |
|---|---|---|---|---|
| Ollama | macOS dmg app with menu bar; Linux `curl \| sh` → systemd service | downloads itself, "Restart to update"; Linux: re-run the script | registered at install | [s29] |
| Syncthing | one binary, or a distro package | upgrades itself within 24 h; packages via the manager; GUI button | wrappers do it | [s30] |
| Docker Desktop | dmg | checks by default, downloads only if asked | off by default | [s31] |
| JupyterLab Desktop | Electron: dmg, exe, deb/rpm/snap, winget | from GitHub releases; no in-app update documented | — | [s32] |
| LM Studio | per-OS installers, headless `llmster`, `lms` daemon | unknown from its docs | unknown | [s37] |
| Claude Code | native installer; casks, winget, apt/dnf/apk | native: background self-update; managers: by hand or on request | — | [s28] |

The pattern: a native launcher owns the server, self-updates from the vendor's feed, offers a login item; a manager install updates by the manager.

## 7. Windows
- Blocked in code, not in packaging: `SessionHost` has one driver, tmux; `TerminalDriver` has kitty (two OS) and Ghostty (macOS);
  `PlatformCheck` accepts `macos` and `linux`; notifications go through osascript, terminal-notifier or notify-send; keep-awake through
  caffeinate or systemd-inhibit; liveness through pgrep, lsof, pkill; `Executables` searches Unix directories; jagt's git hooks run under `sh`.
- tmux ships for Linux and macOS only [s26]; MSYS2 packages tmux 3.8 in its POSIX layer [s35], its pty behaviour under a Windows agent CLI
  unknown; kitty has no Windows build [s27]. Claude Code runs natively on Windows, PowerShell or Git Bash [s28]: the agent is not the blocker.
- Today: WSL2 runs the Linux build; the board on the WSL loopback reaching a Windows browser is not checked here (unknown).
- A native host: a `SessionHost` over ConPTY with detach and re-attach (the hard part: tmux's revive after a jagt restart), a Windows
  Terminal driver, a toast notifier, a keep-awake, path handling, a Windows CI runner. Cost unknown until the spike (task 5); weeks, not days (est).
- An executable that installs on Windows and cannot run a session fails the bar, so none ships before the host.

## 8. Recommendation and tasks — the first run, then the command with its updater, then Windows by measurement, the image last (costs est)
| # | task | replaces | done when | cost |
|---|---|---|---|---|
| 1 | **Self-contained jar**: `jagt.yml.dist`, `mcp_client.js`, `roles/*.dist` on the classpath; `root` = `--root`, `ORCHESTRATOR_ROOT`, else a per-user data dir; reopens 0021 | the clone, 0021 | `java -jar jagt.jar --root=$(mktemp -d)` answers `/state` 200 and shows the setup page; `./gradlew test e2eTest` exit 0 | 3–5 days |
| 2 | **First-run page** on the board: platform read from the machine, every tool probed with its install line per OS, the first project via the `project` verb, the MCP check, `copyGlobs` hint; startup refuses only port and root; step 0: the PWA install prompt measured on 127.0.0.1 and localhost through CDP `Page.getInstallabilityErrors`, the result written into 0117 | README steps 2–4, the refusal list, 0117's open line | `boardTest` scenario: empty root → a task created on a stub project, exit 0 | 3–4 days |
| 3 | **`jagt` command + self-update**: launcher script (tap formula with `service do`, and a curl line) that restarts on a named exit; hourly check of `releases/latest`, download to a new file, sha256 verified, dry-run checks, swap, exit; `ci.yml` attaches the sha256 | `git pull` + build, the manual jar | stand run on a fake feed v0 → v1: the tmux session and a task's state intact, board reconnected; `e2eTest` exit 0 | 3–4 days |
| 4 | **State fixture per release**: the `state.json` of each released minor under test resources, read without loss; the release step adds it | nothing: closes §6.6's gap | `StateCompatibilityTest` exit 0 | 1 day |
| 5 | **Windows host spike**: tmux under MSYS2, ConPTY + Windows Terminal, WSL2-as-Linux, against the `runtime` spec's scenarios; output is a decision record | the open question, not code | the decision merged, with measurements | 2 days |
| 6 | **Conveyor image** (only on 0021's trigger): `conveyor.conf` over the jar, login item, tray if `isSupported()`, Sparkle/MSIX/apt; macOS and Linux until task 5 lands | `java -jar` for a non-developer | each image starts, answers `/state`, updates from a test site; `boardTest` exit 0 | 2–3 days + 99 USD/yr + the signing decision |

Not a task: Electron, Tauri, a webview, GraalVM, homebrew-core, SDKMAN, winget — each rejected (0117), ineligible [s1], or moot before task 5.

## 9. Open questions
- Where does `root` live by default: a per-user data directory, or beside the launcher? It also holds `worktrees/`, which are cut beside it today.
- Does the launcher hold the restart, or the service manager (`brew services`, launchd, systemd)? One of them, never both.
- Does the tap formula pin `openjdk` or `openjdk@25`? Homebrew asks for `openjdk` unless upstream needs a release [s3].
- Is the macOS tray reachable from Java at all? Measured by one `isSupported()` run inside a jpackage image; else Conveyor's launcher only.
- Conveyor's Linux outputs and apt repository come from a 2022 post, secondary; its current output page returned 404 today. Re-check before task 6.
- Does WSL2's loopback reach a Windows browser for the board? Unverified.
- Keys the project would hold: a release signing key (sha256 the minimum) and the Apple account; 0020: jagt holds none, CI secrets are the project's.

## Sources
| id | source | id | source | id | source |
|---|---|---|---|---|---|
| s1 | <https://docs.brew.sh/Package-Acceptance-Policy> | s14 | <https://docs.spring.io/spring-boot/reference/packaging/native-image/introducing-graalvm-native-images.html> | s27 | <https://sw.kovidgoyal.net/kitty/binary/> |
| s2 | <https://docs.brew.sh/Acceptable-Formulae> | s15 | <https://docs.spring.io/spring-boot/how-to/native-image/developing-your-first-application.html> | s28 | <https://code.claude.com/docs/en/setup> |
| s3 | <https://docs.brew.sh/Language-Specific-Formulae> | s16 | <https://www.graalvm.org/latest/reference-manual/native-image/> | s29 | <https://docs.ollama.com/faq>, <https://docs.ollama.com/linux> |
| s4 | <https://docs.brew.sh/How-to-Create-and-Maintain-a-Tap> | s17 | <https://github.com/jreleaser/jreleaser> | s30 | <https://docs.syncthing.net/users/faq.html> |
| s5 | <https://docs.brew.sh/Formula-Cookbook> | s18 | <https://sdkman.io/vendors> | s31 | <https://docs.docker.com/desktop/settings-and-maintenance/settings/>, <https://docs.docker.com/desktop/setup/install/mac-install/> |
| s6 | <https://brew.sh/2025/11/12/homebrew-5.0.0/> | s19 | <https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api> | s32 | <https://github.com/jupyterlab/jupyterlab-desktop> |
| s7 | <https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html> | s20 | <https://developer.chrome.com/blog/run-on-login> | s33 | <https://developer.apple.com/library/archive/documentation/MacOSX/Conceptual/BPSystemStartup/Chapters/CreatingLaunchdJobs.html> |
| s8 | <https://conveyor.hydraulic.dev/latest/> | s21 | <https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/java/awt/SystemTray.html> | s34 | <https://www.freedesktop.org/software/systemd/man/latest/systemd.unit.html> |
| s9 | <https://www.hydraulic.dev/pricing.html> | s22 | <https://sparkle-project.org/documentation/> | s35 | <https://packages.msys2.org/base/tmux> |
| s10 | <https://conveyor.hydraulic.dev/latest/configs/mac/> | s23 | <https://github.com/update4j/update4j> | s36 | <https://bugs.openjdk.org/browse/JDK-8242389> |
| s11 | <https://developer.apple.com/programs/enroll/> | s24 | <https://learn.microsoft.com/en-us/windows/package-manager/package/repository> | s37 | <https://lmstudio.ai/docs/app> |
| s12 | <https://developer.apple.com/news/?id=saqachfa> | s25 | <https://v2.tauri.app/plugin/updater/> | | |
| s13 | <https://support.apple.com/en-us/102445> | s26 | <https://github.com/tmux/tmux/wiki/Installing> | | |
