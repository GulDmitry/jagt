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
A missing session or server lists none; any other failed `list-windows` is unknown: a job or the watchdog decides nothing, a pressed verb is refused.

#### Scenario: The agent exits, or the windows cannot be listed
- **WHEN** the agent process in a window exits
- **THEN** the window shows its tail for 15s, holding no process, and closes, leaving no shell behind
- **WHEN** tmux cannot list the session's windows for any cause but a missing session or server
- **THEN** jagt opens, closes and renames no window, logs `tmux windows unreadable` with the cause, and opens no second window beside one that exists

### Requirement: Closing the viewer only detaches
Closing the viewer SHALL only detach; killing SHALL be explicit — `done` or `close_task_tab`.

#### Scenario: The human closes the kitty window
- **WHEN** the viewer window is closed
- **THEN** the session keeps running until `done` or `close_task_tab`

### Requirement: What jagt revives it re-enters
A session jagt revives SHALL re-enter (`AgentRuntime.reviveCommand`, Claude `--continue`): a lost tmux server costs
the window, not the context. A human's `respawn` SHALL be fresh, as SHALL a review round (`AgentSessions.relayFresh`).
So SHALL a relay past the cache lifetime (`AgentRuntime.pastCacheLifetime` from `Session.idleSince`), read as it is sent.

#### Scenario: The tmux server is lost, a relay comes late, or a round comes early
- **WHEN** jagt revives a session after its tmux server died
- **THEN** the revived session re-enters with `--continue`
- **WHEN** a relay comes after the cache lifetime
- **THEN** the session starts fresh from the current `task_notes.md`, on the model its `next-model:` line names
- **WHEN** a review round comes within the cache lifetime, a headless run wrote after the session, or `respawn` runs
- **THEN** the session starts fresh

### Requirement: A fresh start reads the notes
A fresh start SHALL read `task_notes.md`, rewritten every hand-back and capped (`TaskNotes`). It SHALL run the model
its `next-model: <name>` line names, else `orchestrator.agent.model`, else the CLI default. A name outside
`AgentRuntime.models()` SHALL refuse the hand-back as `validation`, or startup; a runtime that cannot choose ignores both.

#### Scenario: The notes or the config name a model
- **WHEN** a hand-back follows `next-model: gpt`
- **THEN** it is refused as `validation`, naming the allowed models
- **WHEN** `agent.model` is `gpt`
- **THEN** startup is refused, naming `gpt` and the allowed models
- **WHEN** a fresh start finds no `next-model:` line and `agent.model` is `sonnet`
- **THEN** the session launches with `--model sonnet`
- **WHEN** the notes say `next-model: opus` and `agent.model` is `sonnet`
- **THEN** the session launches with `--model opus`

### Requirement: A detached launch gets its own session
A launch SHALL leave jagt's process group, which Ctrl-C reaches and `ProcessBuilder.start()` never leaves:
`ProcessRunner.detachedFrom` runs it under `setsid`, or perl's `POSIX::setsid` where there is none. Both wrappers
`exec`, so `destroy()` on the returned `Process` reaches the app.

#### Scenario: Ctrl-C stops jagt, or a detached binary is missing
- **WHEN** the human presses Ctrl-C in jagt's terminal
- **THEN** an app launched detached keeps running
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
Claude Code's auto-mode classifier blocks calls not pre-approved: the backend SHALL make none; the committed root
`.claude/settings.json` covers sessions there. Every sub-agent worktree's generated `.claude/settings.local.json` MUST
carry `enableAllProjectMcpServers: true`, `permissions.allow: ["mcp__jagt-orchestrator", "Bash(git:*)"]` and `agentAllowedTools`.

#### Scenario: A worktree setting is missing, or the generated settings change
- **WHEN** a worktree lacks either
- **THEN** `ship` / `feedback` stall on an invisible prompt, or `git commit` freezes
- **WHEN** they change while a worktree already exists
- **THEN** it keeps its old file until patched or re-created: only `initialize_task` writes it

### Requirement: The session compacts at 300k tokens
A Claude session SHALL compact at 300k tokens (`ClaudeAgentRuntime.COMPACT_AT_TOKENS`), set on the command too. Its
settings SHALL carry `autoMemoryEnabled: false` and `idleCompaction: false`: idle counts from the turn's end.

#### Scenario: Worktree settings predate the limit
- **WHEN** older settings lack the limit
- **THEN** the command still sets it

### Requirement: The worktree is trusted
`wireAgent` SHALL trust the worktree, replacing only `projects["<worktree>"].hasTrustDialogAccepted` in
`~/.claude.json` (`$CLAUDE_CONFIG_DIR/.claude.json` where set), under an OS file lock every jagt process takes.
`AgentRuntime.beforeLaunch` SHALL check it right before each launch line, writing a lost flag again up to three times.

#### Scenario: The config file is unparsable, the task is removed, or another jagt process writes
- **WHEN** `~/.claude.json` cannot be parsed, the task is removed, or another jagt process is writing it
- **THEN** the file is left alone, the worktree's entry is dropped, or this one waits, so no flag is lost
- **WHEN** the CLI dropped the flag between provisioning and launch
- **THEN** it is written again before the session starts

### Requirement: A session reports itself through its CLI's own hooks
A session SHALL report through its CLI's own hooks, never the model: `HookEndpoint` writes each line, a POST to
`/api/agent/session/<state>` carrying the worktree as `X-Working-Directory`. Each event's state SHALL be one line
of the resource `hooks/<runtime>.properties`, not code.
The payload adds the session's log (else derived from the worktree), which gives last sign of life and spend
(`AgentSpendReader`), and what STARTED it.

#### Scenario: A runtime has no hook resource, or the CLI sends no payload
- **WHEN** a runtime has no `hooks/<runtime>.properties`
- **THEN** no hook is written for it
- **WHEN** a hook fires without a payload
- **THEN** it costs a detail, never the report

### Requirement: What jagt answers a hook, the harness reads
The hook SHALL print jagt's answer. Three hooks are answered: `gate=PreToolUse`, scoped to the shell tool and the file-writing tools (it holds
writes while a plan waits, [task-flow](../task-flow/spec.md)); `turn-end=Stop`; and `prompt-gate=UserPromptSubmit`, answered by
[cold-prompts](../cold-prompts/spec.md). A session is never launched in its CLI's own plan mode. `turn-end` refuses once a turn that leaves the
agent's move unreported (`Move.endsUnreported`); reported means `update_agent_status` since the turn began, recorded on the task,
so a read call is none and a restart changes nothing. It also refuses once a turn that changed the tree and reported nothing
(`TurnTree`), at every status but REVERTED, DEPLOY_CONFLICT and DONE.
It shows the human alone a round handed to the Master (`AgentRuntime.toldTheHuman`).

#### Scenario: A hook is unanswered, or a turn ends
- **WHEN** `gate`, `turn-end` or `prompt-gate` cannot reach jagt
- **THEN** none refuses anything
- **WHEN** a turn at DEPLOYED ends with the tree changed and nothing reported
- **THEN** the end is refused once, and the second end on the same tree passes
- **WHEN** a turn at DEPLOYED ends with the tree as the last end left it
- **THEN** the turn ends
- **WHEN** a turn at IN_PROGRESS began, called `list_tasks` and ends, before or after a restart
- **THEN** the end is refused once

### Requirement: A start after compaction is answered with the task
After a compaction the start SHALL be answered with the task key, also its branch, the title, the board status and the
last report: the open question, else the status message.

#### Scenario: A session starts after compaction
- **WHEN** a start report follows a COMPACTION and the task holds `outcome=progress — v3 routes done`
- **THEN** the answer names its brief, the task as its branch and `your last report: outcome=progress — v3 routes done`
- **AND** every other report is answered empty

### Requirement: jagt reaps a worktree's processes
Each sub-agent spawns its own language server (jdtls ~1–2 GB per Java worktree), never released. jagt SHALL reap
it on `done` (`port/WorktreeProcesses`, `LsofWorktreeProcesses.reap`).
`orchestrator.agent-disabled-plugins` SHALL write `enabledPlugins: {"<name>": false}` into worktree settings, default empty.

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

#### Scenario: A headless run times out, or jagt stops mid-run
- **WHEN** a command times out while a child it spawned is still running
- **THEN** the command and the child are both killed
- **WHEN** jagt stops while it waits on a command
- **THEN** the command is killed, and its caller is told it was stopped (`Processes.Stopped`), never an exit code to judge

### Requirement: The editor is a launcher
jagt SHALL open a worktree and a diff only through `editorCommand` and the optional `editorDiffCommand`, naming no editor.
IDE service folders present in the base repository SHALL be copied into a fresh worktree. A file the worktree already holds SHALL stay as it is.

#### Scenario: IDE service folders, or no diff command
- **WHEN** the base repository holds `.idea/runConfigurations` or `.vscode/launch.json`
- **THEN** a fresh worktree holds the same run configurations or file
- **WHEN** the base repository holds no IDE service folder
- **THEN** nothing is copied
- **WHEN** `editorDiffCommand` is unset and the human presses diff
- **THEN** it is refused, naming the key and an example; jagt still starts

### Requirement: A session's servers can be pinned
With `orchestrator.claude.mcpConfig` set, a session SHALL start and revive with jagt's and that file's servers alone.

#### Scenario: A pinned stand
- **WHEN** `mcpConfig` names a local host's server file
- **THEN** the session reaches jagt and that host, and none of the human's servers
