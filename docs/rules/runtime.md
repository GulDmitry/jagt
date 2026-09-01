# Terminals, sessions and processes

[← AGENTS.md](../../AGENTS.md)

- **The backend owns no terminal**, and **no terminal UI comes back.**
- **No GUI or keystroke automation, ever**: keystrokes land in whatever is focused. Agent terminals are windows
  in a session host (`port/SessionHost`, tmux today), one kitty window attached.
- **One task = one tmux window**; `openTaskWindow` kills same-named ones first. Liveness in a window is the
  child processes of `#{pane_pid}`.
- After the agent exits, its window shows the tail for 15s, then closes. **Never leave an interactive shell in
  an agent window.**
- Closing the viewer only **detaches** it; killing is explicit — `done`, `remove`, `close_task_tab`.

- **A detached launch gets its own session, never an ignored signal.** Ctrl-C reaches jagt's whole process
  **group**, which `ProcessBuilder.start()` never leaves, so `ProcessRunner.detachedFrom` runs it under
  `setsid`, or `perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV'` where there is none. Trapping the signals instead is
  the **wrong** fix: the disposition is inherited by every descendant.
- Both wrappers `exec`, so the returned `Process` is still the app and `destroy()` reaches it; only the
  editor's `runDetached` needs it. A missing binary exits non-zero rather than throwing, so `runDetached`
  **fails** the launch.

- **kitty is one driver, not one per OS**: `AbstractKittyTerminalDriver` holds remote control, the per-session
  socket, tabs, reveal and close; a platform subclass supplies two things, `bringToFront()` and
  `platformOptions()`. macOS needs an AppleScript raise and the Cyrillic `cmd+` keymap, Linux **neither**, so
  `LinuxKittyTerminalDriver` overrides both with nothing.
- `KittyTerminalDriver` drives `kitty @ --to unix:<per-session socket>` against one dedicated instance
  (`--single-instance --instance-group --listen-on -o allow_remote_control=yes`), over tmux (the tab execs
  `tmux attach`). `closeViewerWindow` kills the instance by socket path.

- Claude Code's auto-mode classifier silently blocks tool calls unless pre-approved. The Master needs none;
  the committed root `.claude/settings.json` is for a dev session working **on** jagt.
- Every sub-agent worktree (generated `.claude/settings.local.json`) needs `enableAllProjectMcpServers: true`
  plus `permissions.allow: ["mcp__jagt-orchestrator", "Bash(git:*)"]`: without the first, `ship` / `feedback`
  stall on an invisible prompt; without the second, `git commit` freezes.
- That allow-list is dead text until the worktree is **trusted**, so `wireAgent` records the acceptance:
  `projects["<worktree>"].hasTrustDialogAccepted` in `~/.claude.json` (`$CLAUDE_CONFIG_DIR/.claude.json` where
  set). It replaces that one flag, leaves an unparsable file alone, and drops the entry with the task.
- Safety on shared branches is **not** this allow-list but the detached upstream plus prompt rules. Only
  `initialize_task` regenerates the file, so an **existing** worktree keeps its old one: patch it, or re-create
  the task.

- **A session reports itself through its CLI's own hooks, never through the model**: `HookEndpoint` writes each
  line, a POST to `/api/agent/session/<state>` with the worktree as `X-Working-Directory`.
- **Which of a CLI's events mean what is a resource, not code**:
  `adapter/src/main/resources/hooks/<runtime>.properties`, one line per event; a runtime with no resource writes
  none. The three states are `waiting`, `gone` and `working`.
- Two things the payload buys, neither required: the file the session appends to (otherwise derived from the
  worktree path) and what STARTED it — **a missing payload costs a detail, never the report**. That log is read
  twice: the last sign of life, and the spend (`AgentSpendReader`).
- **What jagt ANSWERS a hook is context, not output**: a harness adds the stdout to the session, so the line
  prints the body (`curl -sf`). jagt answers one thing — a session started from a COMPACTION gets a line
  naming its brief; every other report answers empty.
- **One hook is a gate, and it is declared as one**: `gate=PreToolUse` in the same resource is answered rather
  than recorded, scoped to the shell tool, refusing nothing where unreachable. **This is not a git hook**; the
  ban does not reach it.

- Each sub-agent spawns its **own** language server (jdtls ~1–2 GB per Java worktree), unshareable and kept by the agent,
  so jagt **reaps** each worktree's on `done` / `remove_task` (`reapWorktreeProcesses`: `lsof` by
  cwd, `kill -9`).
- `orchestrator.agent-disabled-plugins` writes `enabledPlugins: {"<name>": false}` into the worktree settings,
  default **empty**.
