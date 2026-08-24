# The pluggable seams and the assistant

[← AGENTS.md](../../AGENTS.md)

## Pluggable by design

**A firm architectural invariant. Do not erode it.**

jagt targets Linux and macOS with swappable terminals, notifiers, editors and AI-agent runtimes (Claude Code /
Codex / Qwen / … — any MCP-capable CLI). Everything OS- or agent-specific lives behind a **strategy
interface**, selected by config, so adding a new one is "implement the interface + register a config value" —
**never** a hardcoded `if claude` / `if macos` sprinkled through the flow.

The agent-agnostic task flow (create worktree → provision → launch → talk over MCP) must stay free of any
single agent's assumptions.

### The six seams

| seam | selected by | today |
|------|-------------|-------|
| `UserNotifier` | `orchestrator.platform` | macos (default), linux |
| `TerminalDriver` | `orchestrator.terminal` | kitty (default), warp |
| `EditorDriver` | `orchestrator.editor-command` | any CLI launcher |
| `AgentRuntime` | `orchestrator.agent` | claude (default), codex |
| `CodeHost` | `orchestrator.code-host.type` | none (default), gitlab, github |
| `Tracker` | `orchestrator.tracker.type` | none (default), jira |

Ports live in `…port`, implementations in `…adapter`.

`JsonHttp` (`…adapter.http`) is the transport both reads go over, and it is **not** a seventh seam: it exists
so a host or a tracker is testable without a socket (every implementation's test drives a fake of it), and it
carries only the verbs a create-or-update needs.

### `AgentRuntime`

The pluggable AI-agent CLI. `launchCommand` **and** worktree provisioning (`provisionWorktree`, a template in
`AbstractAgentRuntime` plus one per-agent hook) both live here.

Liveness is the runtime's too (`lastSessionActivityMillis`): where a CLI keeps a log of a session, only that
runtime knows where — and the same file is what a session's own hooks name back, so the derivation is a
fallback rather than the answer.

`mcp_client.js` is a **standard, agent-agnostic** MCP stdio↔HTTP proxy — keep it that way — and is linked by
the template. Only the config that declares it differs per agent (Claude: `.mcp.json` +
`.claude/settings.local.json`; Codex: `.jagt/codex/config.toml` with `CODEX_HOME` pointed at it — **not** at the
worktree's own `.codex/`, which is where a repository ships the project config layer Codex reads, and jagt
overwriting a tracked file is a change `ship` commits).

**Nothing outside the runtime may name an agent's files** — `WorktreeSetup` only calls `provisionWorktree`, and
`AgentSessions` only `displayName`.

### Which name holds the briefing is the runtime's to answer

The shared system-knowledge file is `AGENTS.md` (`AgentRuntime.SYSTEM_KNOWLEDGE_FILE`). Claude reads
`CLAUDE.md`, so its runtime symlinks `CLAUDE.md` → `AGENTS.md` — one file, never two copies to drift.

`AgentRuntime.systemKnowledgeFile` is asked **before** provisioning; afterwards jagt's own links are
indistinguishable from a checkout. A regular file already on one of those names came out of the checkout, so it
is the **project's**, and taking it costs the agent the instructions the repository ships **and** makes the next
`ship` commit the loss (jagt tracks `CLAUDE.md`; so does one configured project).

Claude's answer is then `CLAUDE.local.md` — loaded exactly the same (verified 2026-08-18), and the one name a
repository does not version. **Every other runtime refuses**, because an agent started without the safety rules
that file carries is worse than a task that would not start.

The bootstrap prompt therefore names **no** file: which one holds the briefing varies, and a prompt that says
`AGENTS.md` is wrong exactly where the fallback applies.

### `CodeHost`

Reads of a review request (the round a sweep decides on, and the branches a `resume` adopts, so neither costs a
model call) plus **exactly one** write: `createOrUpdateMergeRequest`, opening the artifact a human then reviews.

Never a push, a merge, a comment or an approval — those belong to the human's gates or to the agent's own MCP.
**A `CodeHost` that merges is a bug.**

The write is idempotent per (source, target) and **never retitles an open request** (`ship` reruns every review
round, and the human may have edited the title). Its one caller is `ShipService`, and only when a host is
configured.

`ReviewReader` deliberately does **not** fall back to the paid headless read when a configured host fails: that
would spend money invisibly and hide the misconfiguration. A partial REST read must **fail whole** — "no
unresolved comments + green pipeline" advances a task.

**Which protocol a host speaks is its business, not the seam's.** GitHub's read is one GraphQL query because
thread resolution exists nowhere in its REST API, and a round that cannot tell resolved from open relays every
comment it ever saw, forever.

Two GitHub facts a reader will not guess, and that make the difference between advising `deploy` and advising a
fix:

- The substance of a review usually sits in the review **body** rather than in inline threads, so a round read
  from threads alone can miss the whole request — and a CHANGES_REQUESTED decision must never come back with an
  empty comment list.
- `reviewDecision` is only populated where the repository **requires** a review; on an unprotected repo it is
  null however many people clicked Approve, so the reviewers' own latest states are the fallback.

`base-url` is the **web** root (the prefix that decides which URLs the host may claim) and each host derives its
own API endpoints from it — github.com serves its API from another host entirely.

Two flags have no GitHub counterpart on purpose: squash and delete-branch-on-merge are **repository** settings
there, and a `CodeHost` configures no repository.

The relay **line** is shared (`adapter/codehost/RelayLine`), so an agent never has to learn a second format for
a round.

### `Tracker`

Reads the one ticket a launch needs (title, labels, project) so `do <ticket>` costs no model call either.

**Read-only in the strong sense**: a tracker that transitions, comments or assigns is a bug — an issue's state
is the human's to move.

`service/TicketReader` routes it exactly as `ReviewReader` routes a host, including the no-fallback rule: a
tracker that **claimed** the ref owns it, and paying a model to retry the same read spends money invisibly and
hides the misconfiguration.

The assistant keeps one thing no configured tracker can do: follow a URL into a tracker jagt was never pointed
at.

Jira is read over the `v2` API on purpose — Cloud and Data Center both serve it, and the three fields read here
are identical in v2 and v3.

### What a port is

A new agent = one `AgentRuntime` implementation. A Linux port = new `UserNotifier` / `TerminalDriver` /
`EditorDriver` implementations. **Nothing else should need to change.**

## Master assistant

A headless one-shot, and now the **fallback**, not the path: `do <ticket>` needs the ticket read before a
worktree or agent exists, and `service/TicketReader` takes a configured `Tracker` first, `ReviewReader` a
configured `CodeHost` first. With both wired, the only call left is the ⌘K palette, which is a model call by
design.

What the assistant keeps that no configured API has: it **follows a URL** into a tracker — or onto a code host
— jagt was never pointed at.

### How it runs

`HeadlessClaudeAssistant` (`MasterAssistant`) spawns a one-shot
`claude "<prompt>" -p --setting-sources user,project,local --json-schema '<schema>'` (stdin `/dev/null` via
`ProcessRunner`).

It hardcodes **no** MCP server or path: `--setting-sources` makes the child inherit the human's **own** MCP
(portable, OS-independent), and `--json-schema` forces deterministic JSON. It runs from `java.io.tmpdir` so only
user-level MCP loads (no jagt project MCP → fewer tokens).

Project is resolved by intersecting the ticket's labels with each project's `labels`
(`TaskLauncher.projectsMatching`); the title is cached for the commit. Any failure → empty → `do` falls back to
an explicit project.

Headless `-p` does **not** auto-load plugin MCP without `--setting-sources` (verified: default `-p` sees zero
Jira tools), and narrowing it to `project` is equally fatal — the call runs from the temp dir, where project
scope alone resolves to **zero** MCP servers (verified 2026-08-13). **Keep `user` in the list**; the ~7k tokens
it costs are what buys the tracker tools.

### Inheriting is also the cheaper shape

Which is the opposite of what it looks like. An install may **declare** the servers instead
(`assistant.mcp-config` → `--strict-mcp-config`, no credential in jagt because such a file carries `${ENV}`
placeholders), and that is a **determinism knob only** — measured 2026-08-18, $0.09 cold against $0.04, because
the inherited prefix rides the prompt cache the human's own sessions keep warm while a jagt-private one is cold
on almost every call.

It pins the **servers** and nothing else: settings are still loaded, or a declared file's `${ENV}` placeholders
and the model would stop resolving (verified). Declared servers lose their plugin scope in tool names, so an
`allowed-tools` written for the inherited spelling silently stops matching — jagt cannot detect that without
parsing the declaration, so it is documented, not guarded.

### A read that failed is never an answer

**"I could not look" and "there is no such thing" are two different answers, and jagt must never merge them.**
This one cost a live merge request being reported as missing, with nothing in the log at all: the prompt told the
model to answer `exists=false` when it could not read, so a `resume` refused with "or not found" while no request
had ever been fetched.

- Every read's schema carries a **`failure`** string, and the prompt allows it to be empty **only** when the host
  itself answered. Anything that stopped the read — no MCP tool for that host, a tool that errored, auth,
  network, a denied permission — goes in it, naming the tool or server.
- A non-empty `failure` comes back as **empty facts** (unreadable), logged at ERROR with what it says. It never
  becomes `exists=false`.
- On an unreadable read the callers ask `brokenMcpServers()` (`adapter/assistant/McpHealthProbe`, `claude mcp
  list`, free and token-less) and log which servers are down, because the read cannot see that itself. Its
  answer has **three** values, and collapsing the last two is the same bug one layer down: servers are down /
  nothing is down / **could not be established** — the probe failed, was interrupted, or the servers are
  declared through `assistant.mcp-config`, which `claude mcp list` cannot be asked about. A cached answer is
  reused for two minutes, because every probe starts every configured server.
- `permission_denials` in the envelope is logged at ERROR too: a read that was allowed fewer tool calls than it
  tried answered with less than it was asked for.
- The surfaces say which of the two happened, in those words. **Never "could not read (or not found)" again.**

### Every assistant call is metered

It is the only place jagt spends model money. `--output-format json` wraps the schema-validated answer
(`structured_output`, or `result` as a string) together with `usage` and `total_cost_usd`.

`UsageTracker` books it to the task that triggered it (persisted in `state.json`, so it survives a restart) and
to the session (in memory). **A call is billed before its answer is judged** — an errored call was paid for too.

Surfaces: the `TOKENS` dashboard column, the `stats` command and `GET /stats`. Sub-agent spend is **not**
visible here (it lives in the agent's own session) — never present these numbers as a task's total cost.

Measured floor per call (2026-08): ~25k input tokens of CLI baseline context, ~$0.41 on the inherited default
model vs ~$0.06 on haiku — which is why `orchestrator.assistant.model` **ships as `haiku`** (blank it to
inherit the human's own model). **The lever is fewer calls** (deterministic REST reads), not shorter prompts.
