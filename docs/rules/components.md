# Components, sessions and state

[← AGENTS.md](../../AGENTS.md)

## Components

`orchestrator-backend/` is the Spring Boot app: state manager, git lock, MCP HTTP server
(`POST /mcp`), watchdog, auto-review scheduler, macOS automation, board on loopback. Reaching it is the
`AgentRuntime` seam's (`adapter/agent/McpEndpoint`).

- **HTTP** first (Claude): the CLI is pointed at `orchestrator.mcp-url` and carries
  `X-Working-Directory: <worktree>` itself.
- **HTTP** too for OpenCode, in `.jagt/opencode/opencode.json` (0137).
- **stdio** for Codex, which can only *spawn* a server: `AbstractAgentRuntime.linkStdioProxy` links
  `mcp_client.js` (same header, `ECONNREFUSED` retry only).
- `.mcp.json` is Claude Code's project MCP config, **generated per worktree** by `ClaudeAgentRuntime`, never
  symlinked: the header value *is* that path. Codex's is `.jagt/codex/config.toml`.

### Whoever works on jagt reads the same file and reaches the same server

The root is provisioned for both CLIs as a worktree is: **a rule only one vendor loads is a rule half the
sessions break**. Claude reads its `CLAUDE.md` symlink (AGENTS.md) and declares the server, minting the
Master's token, in `.mcp.json`; Codex reads `AGENTS.md` and `.codex/config.toml`, and needs a **trusted** project.

- `jagt.yml` is user config, gitignored, copied from `jagt.desktop.yml.dist`: one `orchestrator` root, re-read per access,
  Spring binding it once for `orchestrator.*`. Sections are omissible value records (`ConfigService.ConfigFile.*Config`).
- Root detection: the nearest parent holding `jagt.desktop.yml.dist` **or** `mcp_client.js` (`OrchestratorPaths`);
  `ORCHESTRATOR_ROOT` overrides.
- `initialize_task` copies gitignored IDE and local files best-effort
  (`WorktreeFiles.copyProjectFiles(…, PROJECT_FILES)`, `copyLocalFiles`); the per-project
  `worktree.copyGlobs`, default `["**/.env"]`, are config, not code.

## `state.json`

SSOT for tasks, gitignored, auto-created. Its statuses are [task-flow](../../openspec/specs/task-flow/spec.md)'s.

- `history`: every status a task moved **to**, with when and **who asked**, oldest first, capped at 50, and
  copied whole into `finished.jsonl` on `done`.
- The asker (`task/turn/ActionOrigin`) rides `service/OriginContext` and is set at an **entry point** only —
  `surface/board/filter/OriginFilter`, `NaturalLanguageDispatch`, `AutoReviewScheduler` — never passed down.
- `StateService` writes atomically and keeps `state.json.bak`, recovering from it and moving an unparsable
  file to `state.json.corrupt`; with no usable backup it **throws** — never make that path fail soft.
- **A dedup key lives with its writer**, on the task (`TaskState.trail`, the `Conflict` of `TaskState.deployment`): never a file
  another writer overwrites, never memory a restart loses; what jagt already did for a step is a mark on `TaskState.turn`.
- **Every status jagt waits at unattended has a progress clock or a ceiling**: the watchdog, the auto-review window,
  `LoopBreaker`, `ConflictStall`; a round sent back to work, red or not ready, through `StepRelay.relayRound`.

## Session roles, tasks and scope

- **A verb runs in the backend process**, no LLM, no MCP round-trip; the **Master** is a root session.
- **`TaskChart`, `TaskReducer`, `Checkers` and `FlowRules` are instances** `FlowWiring` builds from the installed roles;
  a class takes the one it needs through its constructor ([0160](../decisions/0160-the-chart-is-an-instance-and-start-work-is-an-event.md)).
- **Sub-agents** are agent-CLI sessions in worktrees `<data>/tasks/<task slug>/worktrees/<projectKey>`
  ([0168](../decisions/0168-one-data-folder-holds-everything-jagt-writes.md)),
  briefed by `AGENTS.md` (`AgentRuntime.SYSTEM_KNOWLEDGE_FILE`) and `task_context.md`.
- **A task is created with its item's own facts or not at all.** `TaskLauncher` and `TaskResume` read it on every
  launch; `TicketFacts.usable()` gates on a key, a title **and** a link. A failing answer is asked again:
  `protocol/read/RetryPolicy`, 3 attempts, each carrying what the last got wrong. A bare key answered for a
  **different** key is refused, and nothing invents a URL. **A line opening on a project key names no item**: the words after it ARE
  the task and `TaskName.from` cuts its branch out of them.
- **Sub-agents can only act on their own task**: `surface/mcp/refusal/CallerScope` enforces X-Working-Directory; the
  Master presents `surface/mcp/refusal/MasterToken`, written to `master-token` beside `state.json` so a read-only root still
  starts; anyone else is refused. A tool taking a taskId gets a `McpToolScopeTest` row. A sub-agent is listed and
  answered `update_agent_status`, `notify_user` and `open_code` alone, the rest being `Audience.ROOT`; its every call
  bumps `lastActiveTimestamp`. Byte budgets (`WholeApplicationWiringTest`): `tools/list` 14 KB Master, 5 KB sub-agent, launch text 9 KB.
  The debug reads (`read_record`, `read_transcript`, `/api/debug`) are `Audience.ROOT` and write nothing ([server](../server.md)).
- **A task id is any name git accepts as a branch** (`core/task/launch/TaskName`): a task IS its branch, and every
  directory, tmux session, socket and temp file goes through `TaskName.slug`.
- **The MCP transport must never emit non-JSON-RPC bytes**: malformed JSON → `-32700`, an HTTP error → a
  synthesized JSON-RPC error in `mcp_client.js`.
- **`WorktreeOrphanScanner` deletes only its own residue**: an unowned directory with no checkout, no secret
  copy, no bytes. Every other one only WARNs; no surface offers it.

### What is missing is said at startup, not at the click that needed it

- `startup/StartupValidation` asks every `StartupCheck` before the board is announced. It refuses the start
  with **all** problems at once (`Misconfigured` via `StartupFailure`), each line naming its key.
- A check lives **next to** the part it answers for, existing only where that part was selected; what no
  implementation answers for goes in `startup` — a `type` selecting nothing, `jagt.yml`, jagt's paths, git,
  tmux.
- Two limits: **nothing reaches the network** (presence, never validity) and nothing asks a
  remote about a branch. `orchestrator.startup-checks=false` is for test harnesses only and skips the machine
  alone: a check of jagt's own files answers `machine()` false and runs always.
- `surface/board/filter/AbortedConnectionFilter` drops the aborted-peer `setSoLinger` `SocketException`. Never
  silence `NioEndpoint` instead.

## The data folder

`orchestrator.data`: `<root>/data` by default (one `/data/` ignore line), `/data` in the container, built only by `port/host/Layout`
([0190](../decisions/0190-the-data-folder-is-data-under-the-root.md)).

| path | what |
|------|------|
| `jagt/` | `state.json` and its `.bak`, `.corrupt`; every mark: `master-token`, `act-stopped`, `alive`, `*-verdicts` |
| `projects/<name>` | the clone a project url makes |
| `tasks/<task>/` | `worktrees/<project>` (`-deploy`, `-revert`), `plan-baseline.md`, `ticket-text.md`, `scratch/` |
| `archive/`, `records/` | a finished task's snapshot; `*.jsonl`, never read back |
| `memory/`, `logs/` | what the router learned; `jagt-backend.log`, `jagt-master.log` |

An older layout (root, `~/.jagt`, flat here) moves in once, from the folder holding `state.json`: logged, never over a file; its master token goes.
