# Components, sessions and state

[← AGENTS.md](../../AGENTS.md)

## Components

### `orchestrator-backend/`

The Spring Boot app ("The Brain") **and** the Master console itself: state manager, git lock, MCP HTTP server
(`POST /mcp`), watchdog, auto-review scheduler, macOS automation. Run the jar in a real terminal — the process
*is* the Master TUI.

**Outside writes are the sub-agent's job**, via its own MCP: push, merge request, review replies. The backend
makes none of them, ever.

Outside **reads** have one path: a one-shot headless agent that inherits the human's own MCP. **The backend
holds no credential at all**, and nothing in it is configured with a host or a tracker.

### How an agent reaches the MCP server

Part of the `AgentRuntime` seam, and there are exactly two paths (`adapter/agent/McpEndpoint` documents both):

| path | how | used by |
|------|-----|---------|
| **HTTP** (prefer this) | the CLI is pointed at `orchestrator.mcp-url` and carries `X-Working-Directory: <worktree>` itself | Claude, Qwen |
| **stdio** | the CLI can only *spawn* a server, so `AbstractAgentRuntime.linkStdioProxy` gives it `mcp_client.js`, which POSTs the same header | Codex |

HTTP is verified against a real session and is what took Node out of jagt's requirements. `mcp_client.js`
exists **only** for the stdio path — do not link it for everybody again.

A live session survives a backend restart on the HTTP path (measured 2026-08-17 against a real Claude
session): the server keeps no session id, so the next tool call reaches the new process, and a call that
failed with the backend down does not retire the server for the rest of the session. The stdio proxy's
`ECONNREFUSED` retry is therefore not what a restart depends on.

### `.mcp.json`

Claude Code's project MCP config, **generated per worktree** by `ClaudeAgentRuntime` — not symlinked, because
the header value *is* that worktree's path. The committed root `.mcp.json` is the same server for a dev
session working *on* jagt, with no header: that session is not a task, so the backend treats it as Master.

Other runtimes write their own equivalent (Codex: `.jagt/codex/config.toml`). It is not a universal file.

### Whoever works on jagt reads the same file and reaches the same server

Whatever CLI they run. The root is provisioned for all three exactly as a worktree is, because **a rule only
one vendor loads is a rule half the sessions break.**

| CLI | reads | declares jagt's MCP in |
|-----|-------|------------------------|
| Claude | `AGENTS.md` via the `CLAUDE.md` symlink | `.mcp.json` (HTTP) |
| Codex | `AGENTS.md` natively | `.codex/config.toml` (the stdio bridge) |
| Qwen | `AGENTS.md` via `context.fileName` | `.qwen/settings.json` (HTTP, `trust`) |

None of them carries a worktree header, so every root session is Master.

Two limits are Codex's, not jagt's: it loads a project layer only for a **trusted** project and resolves the
bridge relative to where it was launched (start it at the root), and its approval policy is global, so nothing
here pre-approves it the way `.claude/settings.json` pre-approves jagt's own tools for a Claude session.

**A rule that belongs to this repository goes in `AGENTS.md`, never in a vendor-named local file.**

### `config.json`

User config, gitignored, created by copying the committed `config.json.dist`. Grouped into sections:
`projects`, `viewer`, `dashboard`, `codeReview`, `agent`, `worktree`.

Each section is a small value record (`ConfigService.ConfigFile.*Config`) with `defaults()` + `withX` withers
+ `*OrDefault` accessors. A whole section may be omitted — `ConfigFile`'s accessors coalesce a null section to
its defaults, so callers never null-check.

Never commit user-specific paths. **All config keys are documented in `docs/configuration.md` — keep it in
sync.**

### Orchestrator root

Auto-detected at startup: nearest parent directory containing `config.json.dist` **or** `mcp_client.js`
(`OrchestratorPaths`); overridable via `ORCHESTRATOR_ROOT`.

Two markers on purpose — the bridge is only still here for stdio-only agents, so root detection must not
depend on it. No absolute user paths in the repo.

### What `initialize_task` copies into a worktree

All of it is gitignored, hence absent from a fresh branch checkout, and all of it is best-effort (no-op if
absent).

- **IDE project files** (`copyIdeProjectFiles`), so `ide` opens ready to run and query: run configs from both
  `.run/` (modern) and `.idea/runConfigurations/` (legacy, only "Store as project file" ones), plus the DB
  connections (`.idea/dataSources.xml`, `.idea/dataSources.local.xml`, `.idea/dataSources/` — passwords stay
  in the OS keychain keyed by the source UUID, so they carry over).
- **Gitignored local files** matching the per-project `worktree.copyGlobs`, default `["**/.env"]`
  (`copyLocalFiles`, heavy directories skipped). Run configs reference module `.env` files, keys and SSL
  certificates (`app/.env`, `**/*.pem`) that are gitignored and otherwise missing, so the app would not start. **The patterns are
  config, not hardcoded.**

### `state.json`

SSOT for tasks (gitignored, auto-created), written atomically.

Each task keeps `history` — every status it moved **to**, with when and **who asked**, oldest first, capped at
50 (the file is rewritten on every MCP call). A keep-alive adds nothing (same status = no entry, or four real
transitions drown in hundreds of identical rows), and a task starts its history at the status it was created
with.

The asker (`task/ActionOrigin`) is carried by `service/OriginContext` and stamped in `StateService`, **not
passed down**: a deploy reaches the same code whether it was clicked, typed, said in words or called over MCP,
so every signature in between would grow a parameter it has no use for. Set it at an **entry point** only —
`surface/board/OriginFilter` (both HTTP surfaces at once, so a new endpoint cannot forget), `GrammarDispatch.run`,
`NaturalLanguageDispatch` and `AutoReviewScheduler`. Nesting is honest: console free text is recorded as the
interpretation it became.

**Read "since when in this status" from `TaskState.statusSince()`, never from `lastActiveTimestamp`** — a
keep-alive bumps that one, so an hour-old status would look fresh.

| status | means |
|--------|-------|
| `NEW` | created, the agent has not reported yet |
| `IN_PROGRESS` | the agent is working |
| `REVIEW_PENDING` | handed back to the human |
| `SHIPPING` | a push is in flight |
| `CI_POLLING` | a review round is open |
| `CI_FAILED` | checks red |
| `REVIEWED` | nothing unresolved **and** CI green — but not approved |
| `APPROVED` | a human actually approved the review request |
| `DEPLOY_CONFLICT` | deploy hit a merge conflict; a human resolves it in the deploy worktree |
| `DEPLOYED` | live on the deploy branch |
| `REVERTED` | its deploy was taken back out; branch and commits survive, so the next move is a fix |
| `DONE` | closed |

## Session roles

**Master** is the backend process itself. `MasterShell` owns the screen; `surface/console/GrammarDispatch`
parses the fixed grammar and executes it in-process: no LLM, no MCP round-trip, no tokens, no drift.

There is **no Master Claude session** — the deterministic REPL/TUI replaced it, and `master_prompt.md` went
with it. The only LLM call on the master side is the headless one-shot assistant.

**Sub-agents** are Claude sessions in worktrees named `<taskId>-<projectKey>`, siblings of the base repo.
Their generated `CLAUDE.md` carries full system knowledge (orchestrator root, all projects, active tasks) plus
per-task rules; instructions arrive via `task_context.md`.

## Tasks and state

### A task is created with its item's own facts or not at all

The owner's rule, 2026-08-21. `TaskLauncher` reads the reference on **every** launch. The fast path (a bare key
plus an explicit project skipped the read, and a background `TicketTitleBackfill` filled the title in later) is
gone, and so is the backfill: a card being worked on whose ticket link is missing cannot be repaired
afterwards, because no later read can tell an item that **has** no link from one that was never reached.

`TicketFacts.usable()` is the gate — a key, a title **and** a link, all three, because an item that exists has
all three and a card missing one is a card nobody can tell from the next.

An answer that fails it is asked **again** (`TicketReader`: 5 attempts, 2s apart, bounded by two minutes so a
launch a human is waiting on cannot hang on five CLI timeouts), because a model that never found its tracker
tool reports precisely the `exists=false` a deleted item reports. Every negative is re-asked, because every read is a model's.

A bare key whose read answers a **different** key is refused as well, naming both.

The price is deliberate: every `do` pays for one metered model call to read its ticket.

**A source with no summary of its own is not an exception to the gate**, it is the read's job: the prompt has
the reader write a title of its own from the description, since a reader that reached the item at all can name
it in a few words. Nothing invents a URL — that one is read or the launch refuses.

### `state.json` writes

Atomic (temp file + `Files.move` ATOMIC_MOVE) in `StateService`. Atomicity covers a **torn** file, not a **bad**
one, so every write also copies the previous version to `state.json.bak`, and a read that cannot parse the
primary recovers from that backup (moving the bad file to `state.json.corrupt`).

With no usable backup it **throws**: starting with an empty task list over an existing state file would destroy
the human's data on the next write. **Never make that path fail soft.**

### Sub-agents can only act on their own task

X-Working-Directory scoping is enforced in `surface/mcp/CallerScope`, and its wiring into each tool is what
`McpToolScopeTest` pins — the rule was real for three tools and **missing from four** until 2026-08-14, so a
new tool taking a taskId gets a row in that test, not a promise.

`initialize_task` / `remove_task` / `deploy_task` / `revert_task` are Master-only. Task ids are validated
(`[A-Za-z0-9][A-Za-z0-9_-]*`) — they become branch, directory and tmux names.

Every MCP tool call from a registered worktree bumps `lastActiveTimestamp` (the watchdog keep-alive).

### The MCP transport must never emit non-JSON-RPC bytes

Malformed JSON → `-32700` from the controller. HTTP errors → a synthesized JSON-RPC error in `mcp_client.js`
(never forward Spring error pages).

The proxy retries **only** `ECONNREFUSED` (the request was never sent) — other failures may have executed a
non-idempotent tool.

### Watchdog scope is deliberate

`WatchdogService.watches` alerts only for statuses where the **agent** is expected to be working: NEW,
IN_PROGRESS, SHIPPING. Every other status idles by design (CI_POLLING waits on the review request,
REVIEW_PENDING/REVIEWED/APPROVED/DEPLOY_CONFLICT on the human), and watching those turns the alert into noise.

### `WorktreeOrphanScanner` only ever looks

Worktree directories no task owns can hold uncommitted work **and** copies of secrets
(`worktree.copyGlobs`), so it WARNs one line each at startup, plus one desktop ping, and **deletes nothing**.

No surface offers it — the board dialog and `GET /orphans` were removed on the owner's instruction
(2026-08-18), and the console never had a verb for it: housekeeping is not something a human acts on
mid-flight, and the board is dense enough. **Do not add either back.**

It is a job with no interval (once, as soon as the application is up) and it catches nothing itself: a throwing
run is booked against that job by `Jobs`, because a diagnostic must never stop the backend from starting.

### What is missing is said at startup, not at the click that needed it

`startup/StartupValidation` asks every `StartupCheck` before the operator surfaces open and refuses the start
with **all** problems at once (`Misconfigured`, printed by `StartupFailure`) — a human fixes one list instead of
one item per restart, and each line names the key that fixes it.

A check lives **next to** the part it answers for, so it exists only when that part was selected and nothing
branches on which terminal, agent or host is configured (`CliEditorDriver`, the kitty driver,
`TtydWebTerminal`, `LibNotifyNotifier`, `CodexAgentRuntime`). What no implementation can answer for — a `type`
that selects nothing, the human's `config.json`, jagt's own paths, git and tmux — is a check in `startup`.

Two limits are decisions, not gaps: **nothing reaches the network** (presence, never validity — a wrong token
is the first read's answer, and a laptop offline must still start), and nothing asks a remote about a branch
(that is a fetch per project on every start).

`orchestrator.startup-checks=false` belongs to **test harnesses only** — what the checks ask about is the
human's machine, and a runner is not one, so every suite and smoke script that boots the app passes it exactly
as it passes `open-warp-window=false`.

### Tomcat's "Error setting socket options"

A `SocketException` at `setSoLinger` is a connection the peer aborted between `accept()` and configuring it: a
browser pre-connect, the losing half of a Node client's IPv6/IPv4 race to `localhost`, a `curl` probe.
`SO_LINGER` is simply the first unguarded setsockopt, and Tomcat gives no knob (`AbstractProtocol` sets
`connectionLinger` in its constructor).

`surface/board/AbortedConnectionFilter` drops that one event and nothing else. Do **not** "fix" it by silencing
`NioEndpoint`, which also hides real socket errors.
