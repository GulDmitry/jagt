# Terminals, sessions and processes

[← AGENTS.md](../../AGENTS.md)

## Terminals, sessions and processes

### Master shell = full-screen TUI (Lanterna), one integrated screen

`MasterShell` runs a Lanterna `Screen`: command-output log on top, the dashboard table beneath it, the `jagt>`
input line pinned to the bottom row — all in one back-buffer, redrawn from scratch every frame (`render()`),
refreshed every `dashboard.refreshSeconds`. Resize is handled by `doResizeIfNecessary()` plus the full redraw.

**Do not reintroduce a JLine `Status` / scroll-region pinned bar, or any absolute-bottom cursor anchoring.**
That could not survive a terminal resize (DECSTBM resets on resize → an orphaned ghost dashboard and the prompt
flying to row 1), which cost many sessions.

`dashboard.reservedRows` caps the dashboard height so at least that many rows stay for output and input
(overflow → a "… +N" line).

**Terminal layout is testable, never "fix it blind"**: `orchestrator-backend/scripts/dashboard-layout-smoke.sh`
drives the jar in tmux and asserts the invariants (one dashboard header, input pinned to the bottom row,
dashboard above it) across startup, resize both ways, and task-count changes. **Run it after any change to
`MasterShell` rendering** — and pass `--orchestrator.ui=tui`, since the board is the default.

`tui-push-repaint-smoke.sh` is its sibling for the event-driven repaint: refresh 60s plus a status pushed
through `POST /mcp`, so only the listener can explain the redraw. Writing `state.json` directly fires **no**
listener — a test that mutates the file is testing the timer.

No-TTY (e.g. `gradlew bootRun`) falls back to a plain inline line-REPL.

### A detached launch gets its own session, never an ignored signal

`ProcessBuilder.start()` does not leave jagt's process group, and the terminal delivers Ctrl-C to the whole
**group**, so stopping the backend used to SIGINT the IDE jagt had started — one IntelliJ process hosts **every**
project window (measured 2026-08-18: same pgid, child dead on SIGINT).

`ProcessRunner.detachedFrom` therefore runs the command under `setsid`, or under
`perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV'` where there is no `setsid` binary (macOS ships none).

The first attempt was `sh -c "trap '' INT QUIT HUP; exec …"` and it is the **wrong** fix — do not go back to
it: an ignored disposition is inherited by every descendant, so the IDE's own Stop button, Ctrl-C in its
embedded terminal and `kill -QUIT` thread dumps all stopped working for everything it spawned.

Both wrappers `exec`, so the returned `Process` is still the app and `destroy()` reaches it. Agents were never
at risk (the tmux server is already its own session) and kitty daemonizes itself with `--detach`; what **was**
at risk is everything started through `runDetached` — the editor and ttyd.

A wrapper that always starts also means a missing binary is no longer an `IOException`, so `runDetached`
**fails** the launch when the wrapper exits non-zero at once — without that, no ttyd installed reads as "no web
terminal configured".

### No GUI or keystroke automation, ever

System Events keystrokes race with the human typing: they land in whatever is focused.

Agent terminals are windows in a session host (`port/SessionHost`, tmux today); visibility comes from one Warp
window opened via `open warp://launch/jagt-agents` (a launch config generated into
`~/.warp/launch_configurations/`) whenever `tmux list-clients` shows nobody attached.

### kitty is one driver, not one per OS

`AbstractKittyTerminalDriver` holds everything — remote control, the per-session socket, tabs, reveal, close —
and each platform subclass supplies exactly two things: `bringToFront()` and `platformOptions()`.

macOS needs AppleScript to raise the app (Cocoa) and the Cyrillic `cmd+` keymap workaround. Linux needs
**neither** (the WM owns stacking, and kitty's own `ascii` shortcut fallback handles a non-Latin layout), so
`LinuxKittyTerminalDriver` overrides both with nothing and says why.

Selection is `orchestrator.platform` × `orchestrator.terminal` via `@ConditionalOnExpression`, and
`LinuxProfileContextTest` boots the linux profile so a condition typo fails in CI, not on someone's desktop.

`KittyTerminalDriver` drives kitty via its remote-control CLI (`kitty @ --to unix:<per-session socket>`): one
dedicated instance (`--single-instance --instance-group --listen-on -o allow_remote_control=yes`), tabs titled
and closable (unlike Warp). It runs **over tmux** (the tab execs `tmux attach`), so agents persist.
`closeViewerWindow` kills the instance by its socket path — macOS keeps the app alive after windows close, and
`close-os-window` / `--match all` are **not** kitty commands. Tab decoration comes from tmux `set-titles` → the
active window name (taskId).

### tmux

- **One task = one tmux window.** `openTaskWindow` kills same-named windows before spawning.
- Agent liveness in a window is detected via child processes of `#{pane_pid}` — `pane_current_command` always
  reports the shell (no job control in `sh -c` compound commands).
- After the agent exits, its window shows the tail for 15s and closes itself. **Never leave an interactive
  shell in an agent window** — it lingers forever and reads as a hung process.

### Warp

Closing the Warp window only **detaches** the viewer — agents keep running (a tmux feature, by design). Killing
is explicit: `done` / `remove` / `close_task_tab`.

Facts verified empirically plus a docs sweep (2026-07) — do not re-litigate:

- The URI scheme is the **entire** programmatic surface: no CLI, no IPC, no AppleScript dictionary, no MCP for
  the UI.
- Viewer tabs are opened via Tab Configs — TOML generated into `~/.warp/tab_configs/<session>.toml`
  (`[[panes]]` needs a mandatory `id`), opened with `warp://tab_config/<name>` (active window;
  `?new_window=true` for a fresh one). The tab runs `tmux attach` itself, no shell hooks.
- `new_tab` inherits the active tab's group; tab **groups** have zero API.
- Tabs are **not** closable programmatically (absent from the AX tree, no URI, Warp keeps them after process
  death). Whole windows **are** closable via addressed AXPress.

Hence viewMode `shared` is the default; `tab-per-task` leaves dead tabs for the human to close.

### MCP permission gating

Claude Code's auto-mode classifier silently blocks tool calls unless pre-approved.

The Master needs no permissions at all (it is Java; the committed root `.claude/settings.json` exists for a dev
Claude session working **on** jagt, which does call the jagt MCP).

Every sub-agent worktree (generated `.claude/settings.local.json`) needs `enableAllProjectMcpServers: true` plus
`permissions.allow: ["mcp__jagt-orchestrator", "Bash(git:*)"]` — the MCP tools **and** the agent's own git
(commit/push its task branch on `ship`), which nobody in the tmux window is watching to approve. Miss the MCP
entry → `ship`/`feedback` stall on an invisible prompt; miss the git entry → the agent freezes on `git commit`
or `git push`.

Safety on shared branches is **not** this allow-list — it is the detached upstream
(`GitService.detachUpstream`) plus prompt rules. The worktree is the agent's sandbox.

Regenerated only by `initialize_task`, so an **existing** worktree keeps its old file: patch it in place or
re-create the task to pick up a changed allow-list.

### Agent resource hygiene

Each sub-agent is a Claude Code session in a worktree, so each spawns its **own** language server (jdtls
~1–2 GB per Java worktree). They cannot be shared — worktrees have different uncommitted code, and LSP is
per-root.

Agents **keep** their LSP (code intelligence is worth the RAM), so jagt instead **reaps** each worktree's
language server on `done` / `remove_task` (`reapWorktreeProcesses`: `lsof` for processes whose cwd is the
worktree, `kill -9`) — an orphaned or hung jdtls survives the agent's exit otherwise.

`orchestrator.agent-disabled-plugins` writes `enabledPlugins: {"<name>": false}` into the worktree settings —
default **empty** (opt-in for RAM-constrained setups; disabling an absent plugin is a no-op).
