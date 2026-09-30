# Terminals, sessions and processes

[← AGENTS.md](../../AGENTS.md)

- **The backend owns no terminal**; **no terminal UI comes back.**
- **No GUI or keystroke automation, ever**: keystrokes land in whatever is focused. Agent terminals are windows
  in a session host (`port/SessionHost`, tmux today), one kitty window attached.
- **One name = one tmux window**: opening one kills same-named first; liveness there is the child
  processes of `#{pane_pid}`.
- After the agent exits, its window shows the tail for 15s, then closes. **Never leave an interactive shell in
  an agent window.**
- Closing the viewer only **detaches** it; killing is explicit — `done`, `remove`, `close_task_tab`.
- **What jagt revives it re-enters** (`AgentRuntime.reviveCommand`, Claude `--continue`) — a lost tmux server
  costs the window, not the context; a human's `respawn` is fresh.

- **A detached launch gets its own session.** Ctrl-C reaches jagt's whole process
  **group**, which `ProcessBuilder.start()` never leaves, so `ProcessRunner.detachedFrom` runs it under
  `setsid`, or `perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV'` where there is none. Trapping the signals is
  **wrong**: every descendant inherits the disposition.
- Both wrappers `exec`, so the returned `Process` is still the app and `destroy()` reaches it; only the
  editor's `runDetached` needs it. A missing binary exits non-zero rather than throwing, so `runDetached`
  **fails** the launch.

- **kitty is one driver, not one per OS**: `AbstractKittyTerminalDriver` holds remote control, the per-session
  socket, tabs, reveal and close; a platform subclass supplies `bringToFront()` and `platformOptions()` — macOS
  an AppleScript raise and the Cyrillic `cmd+` keymap, Linux **neither**, so `LinuxKittyTerminalDriver`
  overrides both with nothing.
- `KittyTerminalDriver` drives `kitty @ --to unix:<per-session socket>` against one dedicated instance
  (`--single-instance --instance-group --listen-on -o allow_remote_control=yes`), over tmux (the tab execs
  `tmux attach`). `closeViewerWindow` kills the instance by socket path.

- Claude Code's auto-mode classifier silently blocks tool calls unless pre-approved. The backend makes
  none; the committed root `.claude/settings.json` covers sessions running there.
- Every sub-agent worktree (generated `.claude/settings.local.json`) needs `enableAllProjectMcpServers: true`
  plus `permissions.allow: ["mcp__jagt-orchestrator", "Bash(git:*)"]`: without the first, `ship` / `feedback`
  stall on an invisible prompt; without the second, `git commit` freezes.
- That allow-list is dead text until the worktree is **trusted**, so `wireAgent` writes
  `projects["<worktree>"].hasTrustDialogAccepted` in `~/.claude.json` (`$CLAUDE_CONFIG_DIR/.claude.json` where
  set): that one flag replaced, an unparsable file left alone, the entry dropped with the task.
- Safety on shared branches is **not** this allow-list but the detached upstream plus prompt rules. Only
  `initialize_task` writes the file, so an **existing** worktree keeps its old one: patch it, or re-create
  the task.

- **A session reports itself through its CLI's own hooks, never through the model**: `HookEndpoint` writes each
  line, a POST to `/api/agent/session/<state>` carrying the worktree as `X-Working-Directory`.
- **Which of a CLI's events mean what is a resource, not code**:
  `adapter/src/main/resources/hooks/<runtime>.properties`, one line per event, each naming the state it means;
  a runtime with no resource writes none.
- The payload buys two optional things: the file the session appends to (else derived from the
  worktree path) and what STARTED it — **a missing payload costs a detail, never the report**. That log is read
  twice: last sign of life, and spend (`AgentSpendReader`).
- **What jagt ANSWERS a hook, the harness reads** (`curl -sf` prints the body): a start after a COMPACTION gets
  its brief named and the task's facts restated; every other report is answered empty.
- **Two hooks are answered, each declared**: `gate=PreToolUse`, scoped to the shell tool, and `turn-end=Stop`,
  refusing once a turn that leaves the agent's move unreported (`Move.endsUnreported`). Unreachable, both refuse
  nothing. **Not git hooks**; the ban does not reach them.

- Each sub-agent spawns its **own** language server (jdtls ~1–2 GB per Java worktree), unshareable and never released,
  so jagt **reaps** each worktree's on `done` / `remove_task` (`reapWorktreeProcesses`: `lsof` by
  cwd, `kill -9`).
- `orchestrator.agent-disabled-plugins` writes `enabledPlugins: {"<name>": false}` into the worktree settings,
  default **empty**.

## The Master is a headless run per role, not a window

EXPERIMENTAL (`master.mode`): one headless run per role of the brief reads each round in the task's worktrees,
refused every write, commit and push (`HeadlessRoundReviewer`); jagt writes the verdict (`MasterPanel`).
