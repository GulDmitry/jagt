# runtime Specification

## Purpose

How jagt runs agent sessions: session-host terminals, detached processes, the kitty viewer, the agent CLI's settings
and hooks, and what a worktree leaves behind.

## Requirements

### Requirement: The backend owns no terminal
The backend SHALL own no terminal, and no terminal UI SHALL come back. It MUST NOT automate a GUI or keystrokes.
Agent terminals SHALL be windows in a session host (`port/SessionHost`, tmux today), one kitty window attached.

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
Closing the viewer SHALL only detach; killing SHALL be explicit — `done` or `close_task_tab`.

#### Scenario: The human closes the kitty window
- **WHEN** the viewer window is closed
- **THEN** the session keeps running until `done` or `close_task_tab`

### Requirement: What jagt revives it re-enters
A session jagt revives SHALL re-enter (`AgentRuntime.reviveCommand`, Claude `--continue`): a lost tmux server costs
the window, not the context. A human's `respawn` SHALL be fresh, as SHALL a review round (`relayFreshIfChanged`).
So SHALL a relay past the cache lifetime (`AgentRuntime.pastContinuing`), read on the session's own log as it is sent.

#### Scenario: The tmux server is lost
- **WHEN** jagt revives a session after its tmux server died
- **THEN** the revived session re-enters with `--continue`

#### Scenario: A relay comes late
- **WHEN** a relay comes after the cache lifetime
- **THEN** the session starts fresh from the current `task_notes.md`, on the model its `next-model:` line names

#### Scenario: A review round comes early
- **WHEN** a review round comes within the cache lifetime, or a headless run wrote after the session
- **THEN** the session starts fresh

#### Scenario: The human respawns
- **WHEN** the human runs `respawn`
- **THEN** the session starts fresh

### Requirement: A fresh start reads the notes
A fresh start SHALL read `task_notes.md`, rewritten every hand-back and capped (`TaskNotes`). It SHALL run the model
its `next-model: <name>` line names, else the CLI default. A name outside `AgentRuntime.models()` SHALL refuse the
hand-back as `validation`; a runtime that cannot choose ignores it.

#### Scenario: The notes name an unknown model
- **WHEN** a hand-back follows `next-model: gpt`
- **THEN** it is refused as `validation`, naming the allowed models

### Requirement: A detached launch gets its own session
A launch SHALL leave jagt's process group, which Ctrl-C reaches and `ProcessBuilder.start()` never leaves:
`ProcessRunner.detachedFrom` runs it under `setsid`, or perl's `POSIX::setsid` where there is none. Both wrappers
`exec`, so `destroy()` on the returned `Process` reaches the app.

#### Scenario: Ctrl-C stops jagt
- **WHEN** the human presses Ctrl-C in jagt's terminal
- **THEN** an app launched detached keeps running

#### Scenario: A detached binary is missing
- **WHEN** `runDetached` launches a binary that does not exist
- **THEN** the wrapper exits non-zero and the launch fails

### Requirement: kitty is one driver, not one per OS
`AbstractKittyTerminalDriver` SHALL hold remote control, the per-session socket, tabs, reveal and close; a platform
subclass supplies `bringToFront()` and `platformOptions()`: macOS an AppleScript raise and the Cyrillic `cmd+`
keymap, `LinuxKittyTerminalDriver` nothing. `KittyTerminalDriver` SHALL drive one dedicated instance
through its per-session socket, each tab attached to tmux.

#### Scenario: jagt closes the viewer
- **WHEN** `closeViewerWindow` runs
- **THEN** the kitty instance is killed by its socket path

### Requirement: Every call a session needs is pre-approved
Claude Code's auto-mode classifier blocks calls not pre-approved: the backend SHALL make none; the
committed root `.claude/settings.json` covers sessions there. Every sub-agent worktree's generated
`.claude/settings.local.json` MUST carry `enableAllProjectMcpServers: true` plus
`permissions.allow: ["mcp__jagt-orchestrator", "Bash(git:*)"]` and every tool in `agentAllowedTools`.

#### Scenario: A worktree setting is missing
- **WHEN** a worktree lacks either
- **THEN** `ship` / `feedback` stall on an invisible prompt, or `git commit` freezes

#### Scenario: The generated settings change
- **WHEN** they change while a worktree already exists
- **THEN** it keeps its old file until patched or re-created: only `initialize_task` writes it

### Requirement: The session compacts at 300k tokens
A Claude session SHALL compact at 300k tokens (`ClaudeAgentRuntime.COMPACT_AT_TOKENS`), set on the command too. Its
settings SHALL carry `autoMemoryEnabled: false`.

#### Scenario: Worktree settings predate the limit
- **WHEN** older settings lack the limit
- **THEN** the command still sets it

### Requirement: The worktree is trusted
`wireAgent` SHALL trust the worktree, writing
`projects["<worktree>"].hasTrustDialogAccepted` in `~/.claude.json` (`$CLAUDE_CONFIG_DIR/.claude.json` where set),
replacing that one flag.

#### Scenario: The config file is unparsable or the task is removed
- **WHEN** `~/.claude.json` cannot be parsed, or the task is removed
- **THEN** the file is left alone, or the worktree's entry is dropped

### Requirement: A session reports itself through its CLI's own hooks
A session SHALL report through its CLI's own hooks, never the model: `HookEndpoint` writes each line, a POST to
`/api/agent/session/<state>` carrying the worktree as `X-Working-Directory`. Each event's state SHALL be one line
of the resource `hooks/<runtime>.properties`, not code.
The payload adds the session's log (else derived from the worktree), which gives last sign of life and spend
(`AgentSpendReader`), and what STARTED it.

#### Scenario: A runtime has no hook resource
- **WHEN** a runtime has no `hooks/<runtime>.properties`
- **THEN** no hook is written for it

#### Scenario: The CLI sends no payload
- **WHEN** a hook fires without a payload
- **THEN** it costs a detail, never the report

### Requirement: What jagt answers a hook, the harness reads
The hook SHALL print jagt's answer. Two hooks are answered:
`gate=PreToolUse`, scoped to the shell tool and the file-writing tools (it holds writes while a plan waits,
[task-flow](../task-flow/spec.md)); and `turn-end=Stop`.
A session is never launched in its CLI's own plan mode. The latter refuses once a turn that leaves the
agent's move unreported (`Move.endsUnreported`), and shows the human alone a round handed to the Master
(`AgentRuntime.toldTheHuman`). After a compaction the start SHALL be answered with the task key, also its branch,
the title, the board status and the last report: the open question, else the status message.

#### Scenario: A session starts after compaction
- **WHEN** a start report follows a COMPACTION and the task holds `outcome=progress — v3 routes done`
- **THEN** the answer names its brief, the task as its branch and `your last report: outcome=progress — v3 routes done`
- **AND** every other report is answered empty

#### Scenario: jagt is unreachable
- **WHEN** `gate` or `turn-end` cannot reach jagt
- **THEN** neither refuses anything

### Requirement: jagt reaps a worktree's processes
Each sub-agent spawns its own language server (jdtls ~1–2 GB per Java worktree), never released. jagt SHALL reap
it on `done` (`port/WorktreeProcesses`, `LsofWorktreeProcesses.reap`).
`orchestrator.agent-disabled-plugins` SHALL write `enabledPlugins: {"<name>": false}` into worktree settings,
default empty.

#### Scenario: A Java task is done
- **WHEN** `done` runs
- **THEN** every process whose cwd is in the worktree is killed

### Requirement: A line in the agent file names its gap
A REVIEW_PENDING SHALL be refused past 3 lines the task added to the repository's agent files
(`AgentRuntime.projectAgentFiles`). It SHALL be refused while `task_notes.md` holds fewer `agent-file:` lines.

#### Scenario: Two lines added, one named
- **WHEN** a hand-back follows two added lines and one `agent-file:` line
- **THEN** it is refused, as when git cannot count

### Requirement: A waited-on command leaves no process behind
A command jagt waits on (`ProcessRunner.run`) SHALL be killed with every descendant when its timeout passes, when the
wait is interrupted and when jagt stops. A detached launch SHALL be left running: it outlives jagt by design.

#### Scenario: A headless run times out
- **WHEN** a command times out while a child it spawned is still running
- **THEN** the command and the child are both killed

#### Scenario: jagt stops mid-run
- **WHEN** jagt stops while it waits on a command
- **THEN** the command is killed

### Requirement: The editor is a launcher
jagt SHALL open a worktree and a diff only through `editorCommand` and the optional `editorDiffCommand`, naming no editor.
IDE service folders present in the base repository SHALL be copied into a fresh worktree.
A file the worktree already holds SHALL stay as it is.

#### Scenario: IntelliJ run configurations
- **WHEN** the base repository holds `.idea/runConfigurations`
- **THEN** a fresh worktree holds the same run configurations

#### Scenario: VS Code launch file
- **WHEN** the base repository holds `.vscode/launch.json`
- **THEN** a fresh worktree holds the same file

#### Scenario: Neither IDE
- **WHEN** the base repository holds no IDE service folder
- **THEN** nothing is copied

#### Scenario: No diff command
- **WHEN** `editorDiffCommand` is unset and the human presses diff
- **THEN** it is refused, naming the key and an example; jagt still starts
