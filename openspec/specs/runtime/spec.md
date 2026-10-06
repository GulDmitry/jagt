# runtime Specification

## Purpose

How jagt runs agent sessions: terminals in a session host, detached processes, the kitty viewer, the agent CLI's
settings and hooks, and the processes a worktree leaves behind.

## Requirements

### Requirement: The backend owns no terminal
The backend SHALL own no terminal, and no terminal UI SHALL come back. It MUST NOT automate a GUI or keystrokes:
they land in whatever is focused. Agent terminals SHALL be windows in a session host (`port/SessionHost`, tmux
today), one kitty window attached.

#### Scenario: An agent terminal opens
- **WHEN** jagt starts an agent session
- **THEN** it runs in a session-host window, viewed through the one attached kitty window

### Requirement: One name is one tmux window
Opening a window SHALL first kill any same-named one; liveness SHALL be the child processes of `#{pane_pid}`. An
exited agent's window SHALL close after showing its tail 15s; an interactive shell MUST NEVER be left in it.

#### Scenario: The agent exits
- **WHEN** the agent process in a window exits
- **THEN** the window shows its tail for 15s and closes, leaving no shell behind

### Requirement: Closing the viewer only detaches
Closing the viewer SHALL only detach; killing SHALL be explicit — `done`, `remove`, `close_task_tab`.

#### Scenario: The human closes the kitty window
- **WHEN** the viewer window is closed
- **THEN** the session keeps running until `done`, `remove` or `close_task_tab`

### Requirement: What jagt revives it re-enters
A session jagt revives SHALL re-enter (`AgentRuntime.reviveCommand`, Claude `--continue`): a lost tmux server costs
the window, not the context. A human's `respawn` SHALL be fresh. A relay past the cache lifetime
(`AgentRuntime.continuesWithin`, Claude 1h) SHALL start fresh from `task_notes.md`: rewritten every hand-back,
capped (`TaskNotes`).

#### Scenario: The tmux server is lost
- **WHEN** jagt revives a session after its tmux server died
- **THEN** the revived session re-enters with `--continue`

#### Scenario: A relay comes late
- **WHEN** a relay comes after the cache lifetime
- **THEN** the session starts fresh from the current `task_notes.md`

#### Scenario: The human respawns
- **WHEN** the human runs `respawn`
- **THEN** the session starts fresh

### Requirement: A detached launch gets its own session
Ctrl-C reaches jagt's whole process group, which `ProcessBuilder.start()` never leaves, so
`ProcessRunner.detachedFrom` SHALL run it under `setsid`, or `perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV'` where
there is none. Trapping the signals is wrong: every descendant inherits the disposition. Both wrappers `exec`, so
`destroy()` on the returned `Process` reaches the app (only the editor's `runDetached` needs it).

#### Scenario: Ctrl-C stops jagt
- **WHEN** the human presses Ctrl-C in jagt's terminal
- **THEN** an app launched detached keeps running

#### Scenario: A detached binary is missing
- **WHEN** `runDetached` launches a binary that does not exist
- **THEN** the wrapper exits non-zero and the launch fails

### Requirement: kitty is one driver, not one per OS
`AbstractKittyTerminalDriver` SHALL hold remote control, the per-session socket, tabs, reveal and close; a platform
subclass supplies `bringToFront()` and `platformOptions()`: macOS an AppleScript raise and the Cyrillic `cmd+`
keymap, `LinuxKittyTerminalDriver` nothing. `KittyTerminalDriver` SHALL drive `kitty @ --to unix:<per-session socket>`
against one dedicated instance (`--single-instance --instance-group --listen-on -o allow_remote_control=yes`), over
tmux (the tab execs `tmux attach`).

#### Scenario: jagt closes the viewer
- **WHEN** `closeViewerWindow` runs
- **THEN** the kitty instance is killed by its socket path

### Requirement: Every call a session needs is pre-approved
Claude Code's auto-mode classifier silently blocks calls not pre-approved: the backend SHALL make none, and the
committed root `.claude/settings.json` covers sessions there. Every sub-agent worktree's generated
`.claude/settings.local.json` MUST carry `enableAllProjectMcpServers: true` plus
`permissions.allow: ["mcp__jagt-orchestrator", "Bash(git:*)"]`. Shared branches are guarded by the detached
upstream and prompt rules, not this allow-list.

#### Scenario: A worktree setting is missing
- **WHEN** a worktree lacks `enableAllProjectMcpServers`, or lacks the allow-list
- **THEN** `ship` / `feedback` stall on an invisible prompt, or `git commit` freezes

#### Scenario: The generated settings change
- **WHEN** they change while a worktree already exists
- **THEN** it keeps its old file until patched or re-created: only `initialize_task` writes it

### Requirement: The session compacts at 300k tokens
A Claude session SHALL compact at 300k tokens (`ClaudeAgentRuntime.COMPACT_AT_TOKENS`), set on the command too.

#### Scenario: Worktree settings predate the limit
- **WHEN** a session starts with older settings lacking the limit
- **THEN** the command still sets it

### Requirement: The worktree is trusted
The allow-list is dead text until the worktree is trusted, so `wireAgent` SHALL write
`projects["<worktree>"].hasTrustDialogAccepted` in `~/.claude.json` (`$CLAUDE_CONFIG_DIR/.claude.json` where set),
replacing that one flag.

#### Scenario: The config file is unparsable or the task is removed
- **WHEN** `~/.claude.json` cannot be parsed, or the task is removed
- **THEN** the file is left alone, or the worktree's entry is dropped

### Requirement: A session reports itself through its CLI's own hooks
A session SHALL report through its CLI's own hooks, never the model: `HookEndpoint` writes each line, a POST to
`/api/agent/session/<state>` carrying the worktree as `X-Working-Directory`. Which event means what SHALL be a
resource, not code: `adapter/src/main/resources/hooks/<runtime>.properties`, one line per event naming its state.
The payload adds the session's log (else derived from the worktree), which gives last sign of life and spend
(`AgentSpendReader`), and what STARTED it.

#### Scenario: A runtime has no hook resource
- **WHEN** a runtime has no `hooks/<runtime>.properties`
- **THEN** no hook is written for it

#### Scenario: The CLI sends no payload
- **WHEN** a hook fires without a payload
- **THEN** it costs a detail, never the report

### Requirement: What jagt answers a hook, the harness reads
The hook SHALL print jagt's answer (`curl -sf` prints the body). Two hooks are answered, each declared:
`gate=PreToolUse`, scoped to the shell tool, and `turn-end=Stop`, refusing once a turn that leaves the agent's move
unreported (`Move.endsUnreported`), and showing the human alone a round handed to the Master
(`AgentRuntime.toldTheHuman`). They are not git hooks; that ban does not reach them.

#### Scenario: A session starts after compaction
- **WHEN** a start report follows a COMPACTION
- **THEN** the answer names its brief and restates the task's facts
- **AND** every other report is answered empty

#### Scenario: jagt is unreachable
- **WHEN** `gate` or `turn-end` cannot reach jagt
- **THEN** neither refuses anything

### Requirement: jagt reaps a worktree's processes
Each sub-agent spawns its own language server (jdtls ~1–2 GB per Java worktree), never released, so jagt SHALL reap
it on `done` / `remove_task` (`port/WorktreeProcesses`, `LsofWorktreeProcesses.reap`: `lsof` by cwd, `kill -9`).
`orchestrator.agent-disabled-plugins` SHALL write `enabledPlugins: {"<name>": false}` into worktree settings,
default empty.

#### Scenario: A Java task is done
- **WHEN** `done` or `remove_task` runs
- **THEN** every process whose cwd is in the worktree is killed
