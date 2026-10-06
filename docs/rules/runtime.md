# Terminals, sessions and processes

[← AGENTS.md](../../AGENTS.md)

- **The backend owns no terminal**; **no terminal UI comes back.**
- **No GUI or keystroke automation, ever**: keystrokes land in whatever is focused. Agent terminals are windows
  in a session host (`port/SessionHost`, tmux today), one kitty window attached.
- **One name = one tmux window**: opening one kills same-named first; liveness there is the child
  processes of `#{pane_pid}`.
- An exited agent's window closes after showing its tail 15s. **Never leave an interactive shell in
  an agent window.**
- Closing the viewer only **detaches**; killing is explicit — `done`, `remove`, `close_task_tab`.
- **What jagt revives it re-enters** (`AgentRuntime.reviveCommand`, Claude `--continue`) — a lost tmux server
  costs the window, not the context; a human's `respawn` is fresh.
- **A relay past the cache lifetime starts fresh** (`AgentRuntime.continuesWithin`, Claude 1h), from `task_notes.md`:
  rewritten every hand-back, capped (`TaskNotes`).

- **A detached launch gets its own session.** Ctrl-C reaches jagt's whole process
  **group**, which `ProcessBuilder.start()` never leaves, so `ProcessRunner.detachedFrom` runs it under
  `setsid`, or `perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV'` where there is none. Trapping the signals is
  **wrong**: every descendant inherits the disposition.
- Both wrappers `exec`, so `destroy()` on the returned `Process` reaches the app (only the editor's
  `runDetached` needs it); a missing binary exits non-zero, so `runDetached` **fails** the launch.

- **kitty is one driver, not one per OS**: `AbstractKittyTerminalDriver` holds remote control, the per-session
  socket, tabs, reveal and close; a platform subclass supplies `bringToFront()` and `platformOptions()`: macOS
  an AppleScript raise and the Cyrillic `cmd+` keymap, `LinuxKittyTerminalDriver` **nothing**.
- `KittyTerminalDriver` drives `kitty @ --to unix:<per-session socket>` against one dedicated instance
  (`--single-instance --instance-group --listen-on -o allow_remote_control=yes`), over tmux (the tab execs
  `tmux attach`). `closeViewerWindow` kills the instance by socket path.

- Claude Code's auto-mode classifier silently blocks calls not pre-approved: the backend makes none, the
  committed root `.claude/settings.json` covers sessions there.
- Every sub-agent worktree (generated `.claude/settings.local.json`) needs `enableAllProjectMcpServers: true`
  plus `permissions.allow: ["mcp__jagt-orchestrator", "Bash(git:*)"]`: without the first, `ship` / `feedback`
  stall on an invisible prompt; without the second, `git commit` freezes.
- It compacts at 300k tokens (`ClaudeAgentRuntime.COMPACT_AT_TOKENS`), set on the command too: older settings lack it.
- That allow-list is dead text until the worktree is **trusted**, so `wireAgent` writes
  `projects["<worktree>"].hasTrustDialogAccepted` in `~/.claude.json` (`$CLAUDE_CONFIG_DIR/.claude.json` where
  set): that one flag replaced, an unparsable file left alone, the entry dropped with the task.
- Shared branches are guarded by the detached upstream and prompt rules, **not** this allow-list. Only
  `initialize_task` writes the file: an **existing** worktree keeps its old one until patched or re-created.

- **A session reports itself through its CLI's own hooks, never through the model**: `HookEndpoint` writes each
  line, a POST to `/api/agent/session/<state>` carrying the worktree as `X-Working-Directory`.
- **Which of a CLI's events mean what is a resource, not code**:
  `adapter/src/main/resources/hooks/<runtime>.properties`, one line per event naming its state; a runtime
  with no resource writes none.
- The payload adds the session's log (else derived from the worktree) and what STARTED it —
  **a missing payload costs a detail, never the report**. That log gives last sign of life and spend (`AgentSpendReader`).
- **What jagt ANSWERS a hook, the harness reads** (`curl -sf` prints the body): a start after a COMPACTION gets
  its brief named and the task's facts restated; every other report is answered empty.
- **Two hooks are answered, each declared**: `gate=PreToolUse`, scoped to the shell tool, and `turn-end=Stop`,
  refusing once a turn that leaves the agent's move unreported (`Move.endsUnreported`), and showing the human
  alone a round handed to the Master (`AgentRuntime.toldTheHuman`). Unreachable, both refuse nothing.
  **Not git hooks**; the ban does not reach them.

- Each sub-agent spawns its **own** language server (jdtls ~1–2 GB per Java worktree), never released, so jagt
  **reaps** it on `done` / `remove_task` (`reapWorktreeProcesses`: `lsof` by cwd, `kill -9`).
- `orchestrator.agent-disabled-plugins` writes `enabledPlugins: {"<name>": false}` into worktree settings,
  default **empty**.

## The Master is a headless run per role, not a window

EXPERIMENTAL (`master.mode`): one headless run per role of the brief reads each round in the task's worktrees,
refused every write, commit and push (`HeadlessClaudeRoundReviewer`); jagt writes the verdict (`MasterPanel`).
**Every read quotes the human's own words to the session** (`AgentRuntime.humanSaid`), overruling any decision
taken for them; unreadable, they stop the round ([0010](../decisions/0010-the-humans-own-word-stands-over-the-master.md)).
