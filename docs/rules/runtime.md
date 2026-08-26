# Terminals, sessions and processes

[← AGENTS.md](../../AGENTS.md)

## Terminals, sessions and processes

### The backend owns no terminal

The Master is the backend process and nothing more: it prints where the board is and stays out of the
terminal's way, so its own log lines are the only thing on screen.

**Do not reintroduce a terminal UI.** A full-screen console shipped beside the board until 2026-08-26 and was
removed: it duplicated every verb and every projection the board already had, and a second surface is a second
place for a capability to be missing from.

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
at risk is everything started through `runDetached` — the editor.

A wrapper that always starts also means a missing binary is no longer an `IOException`, so `runDetached`
**fails** the launch when the wrapper exits non-zero at once — without that, a missing binary reads as a
launch that worked.

Each launch logs its pid and whether it got a session of its own, and its end logs what ended it and how long
it lived — `128 + signal` naming the signal. Without those two lines an IDE that died cannot be told from one
that was quit. The line says what arrived, never who sent it: jagt's own shutdown terminates what it started,
so a TERM beside the shutdown lines is jagt's, and one on its own is not.

### No GUI or keystroke automation, ever

System Events keystrokes race with the human typing: they land in whatever is focused.

Agent terminals are windows in a session host (`port/SessionHost`, tmux today); visibility comes from one
kitty window attached to that session whenever `tmux list-clients` shows nobody attached.

### kitty is one driver, not one per OS

`AbstractKittyTerminalDriver` holds everything — remote control, the per-session socket, tabs, reveal, close —
and each platform subclass supplies exactly two things: `bringToFront()` and `platformOptions()`.

macOS needs AppleScript to raise the app (Cocoa) and the Cyrillic `cmd+` keymap workaround. Linux needs
**neither** (the WM owns stacking, and kitty's own `ascii` shortcut fallback handles a non-Latin layout), so
`LinuxKittyTerminalDriver` overrides both with nothing and says why.

Selection is `orchestrator.platform` alone, and `LinuxProfileContextTest` boots the linux profile so a
condition typo fails in CI, not on someone's desktop. The comparison ignores case, like every other property
condition: `platform: Linux` picking the Linux notifier and no terminal at all would be a value that means one
thing in one place and nothing in another.

`KittyTerminalDriver` drives kitty via its remote-control CLI (`kitty @ --to unix:<per-session socket>`): one
dedicated instance (`--single-instance --instance-group --listen-on -o allow_remote_control=yes`), tabs titled
and closable. It runs **over tmux** (the tab execs `tmux attach`), so agents persist.
`closeViewerWindow` kills the instance by its socket path — macOS keeps the app alive after windows close, and
`close-os-window` / `--match all` are **not** kitty commands. Tab decoration comes from tmux `set-titles` → the
active window name (taskId).

### tmux

- **One task = one tmux window.** `openTaskWindow` kills same-named windows before spawning.
- Agent liveness in a window is detected via child processes of `#{pane_pid}` — `pane_current_command` always
  reports the shell (no job control in `sh -c` compound commands).
- After the agent exits, its window shows the tail for 15s and closes itself. **Never leave an interactive
  shell in an agent window** — it lingers forever and reads as a hung process.

### The viewer

Closing the viewer only **detaches** it — agents keep running (a tmux feature, by design). Killing is explicit:
`done` / `remove` / `close_task_tab`.

A Warp driver shipped beside kitty's until 2026-08-26 and was removed. Do not re-litigate it: Warp's URI scheme
is its **entire** programmatic surface — no CLI, no IPC, no AppleScript dictionary — and a tab it opens cannot
be titled, addressed or closed again, which is exactly what a per-task viewer needs.

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

### A session reports itself through its CLI's own hooks, never through the model

The same generated file declares them, and `adapter/agent/HookEndpoint` writes the one line each runs: a POST
to `/api/agent/session/<state>` carrying the worktree as `X-Working-Directory`, with whatever the harness handed
the hook forwarded on stdin unread by the shell. The line ends in `|| true`, because a failure reported here
would put jagt's plumbing in front of the human working in that session.

**Which of a CLI's events mean what is a resource, not code**: `adapter/src/main/resources/hooks/<runtime>.properties`,
one line per event, so the mapping a human comes looking for is one file to open and the vendor's vocabulary
never reaches Java. A runtime with no resource writes no hooks. jagt's three states are `waiting` (nothing moves
until a human answers), `gone` and `working`.

Two things the payload buys, and neither is required: the file the session appends to (otherwise derived from
the worktree path, which is a guess) and what STARTED the session. **A missing or changed payload costs a
detail, never the report.**

The log that file names is read twice over: for the last sign of life, and for what the session spent
(`AgentSpendReader`, from the mark the task carries, so an append adds only what is new).

**What jagt ANSWERS a hook is context, not output.** A harness adds a hook's stdout to the session, so the line
prints the body (`curl -sf`, which prints nothing at all on a failure — an error page in a model's context is
worse than silence). jagt answers exactly one thing: a session that started from a COMPACTION gets one line
naming its brief, because a compaction drops it silently and an agent that cannot see the rules starts breaking
them. Every other report answers empty. This is the only model-facing text jagt writes outside a task's own
brief — do not grow it into a second briefing.

A report has `WatchdogService.check` judge that one task at once — otherwise a session answered by a human
would keep its NEEDS YOU for a whole probe interval, which is exactly what a ten-minute interval makes
unbearable.

**One hook is a gate, and it is declared as one.** `gate=PreToolUse` in the same resource is answered rather
than recorded: `POST /api/agent/tool` refuses a push whose destination is not the task's own branch
(`ToolGate`) and answers everything else with nothing. It is scoped to the shell tool, so no other call waits on
jagt, and an unreachable jagt refuses nothing — a stopped backend must not read as a rule.

**This is not a git hook** and the ban does not reach it: a git hook enforces for every user of the repository
and cannot be reasoned with, while this one refuses a single command in a single session and hands the model the
reason. The verdicts stay jagt's own (`WatchdogService`, `ToolGate`).

### Agent resource hygiene

Each sub-agent is a Claude Code session in a worktree, so each spawns its **own** language server (jdtls
~1–2 GB per Java worktree). They cannot be shared — worktrees have different uncommitted code, and LSP is
per-root.

Agents **keep** their LSP (code intelligence is worth the RAM), so jagt instead **reaps** each worktree's
language server on `done` / `remove_task` (`reapWorktreeProcesses`: `lsof` for processes whose cwd is the
worktree, `kill -9`) — an orphaned or hung jdtls survives the agent's exit otherwise.

`orchestrator.agent-disabled-plugins` writes `enabledPlugins: {"<name>": false}` into the worktree settings —
default **empty** (opt-in for RAM-constrained setups; disabling an absent plugin is a no-op).
