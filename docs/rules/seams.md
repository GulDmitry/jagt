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

### The four seams

| seam | selected by | today |
|------|-------------|-------|
| `UserNotifier` | `orchestrator.platform` | macos (default), linux |
| `TerminalDriver` | `orchestrator.terminal` | kitty (default), warp |
| `EditorDriver` | `orchestrator.editor-command` | any CLI launcher |
| `AgentRuntime` | `orchestrator.agent` | claude (default), codex |

Ports live in `…port`, implementations in `…adapter`.

**A seam selected for the wrong OS is refused at startup, never degraded** (`adapter/PlatformCheck`): the
default is macOS, and telling a human is the one thing that may not fail the flow it interrupts — so a notifier
that reaches nothing logs and returns, and the human learns of no blocked session at all. `warp` is refused off
macOS for the same reason.

**The tracker and the code host are not seams of jagt's.** They are read by a model through the MCP servers of
whoever runs it, and jagt holds no credential for either. An orchestrator that reads them itself is an open
idea, in `TODO.md`.

### `AgentRuntime`

The pluggable AI-agent CLI. `launchCommand` **and** worktree provisioning (`provisionWorktree`, a template in
`AbstractAgentRuntime` plus one per-agent hook) both live here.

Liveness is the runtime's too (`lastSessionActivity`): where a CLI keeps a log of a session, only that
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

### What a port is

A new agent = one `AgentRuntime` implementation. A Linux port = new `UserNotifier` / `TerminalDriver` /
`EditorDriver` implementations. **Nothing else should need to change.**

### A port answers what it achieved, never a value the caller has to interpret

An adapter knows what it can do; a caller can only guess, and a guess written for one implementation is a lie
in the next.

- `TerminalDriver.reveal` returns `Revealed` — `WINDOW`, `UNREACHABLE_TAB`, `NOT_RUNNING`. It was a boolean,
  and `focus` had one sentence for false: "the agents viewer is a TAB, the terminal has no API to switch tabs".
  True for Warp, whose viewer IS a tab in somebody else's window. A lie for kitty, whose false means its
  instance is not running at all — the viewer gets its own window there and never is a tab.
- `AgentRuntime.lastSessionActivity` returns `OptionalLong`. It answered 0 both for "this runtime keeps no
  record of itself" and for a record holding no entry yet — "there is no such clock" is not a reading of one,
  and a caller that cannot tell them apart has to treat every runtime as the poorest of them. A read that
  FAILS is a third thing again, and stays the adapter's own to report: `ClaudeTranscripts` logs it rather than
  passing a zero up, which is [a read that failed is never an
  answer](#a-read-that-failed-is-never-an-answer) one layer down.

A capability nothing branches on needs no declaration. When something does branch, the port carries the answer
— never a boolean plus a comment, and never a magic value.

## Master assistant

A headless one-shot, and the **only** way jagt reads anything outside itself: `service/TicketReader` for the
ticket a `do` needs before a worktree or agent exists, `service/ReviewReader` for the round a sweep decides on
and the branches a `resume` adopts. The ⌘K palette is the third caller, and a model call by design.

What it can do that no configured API could: **follow a URL** into a tracker or onto a code host jagt was never
told about. What it cannot: reach a server that only an interactive login authenticates — a print-mode session
authenticates none, and plugin-scoped servers do not load in it at all. Such a server has to be declared to the
read with `orchestrator.assistant.mcp-config`.

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

A sub-agent's own spend is read separately, from the log that session keeps of itself
(`AgentSpendReader`, tokens only — the log prices nothing), and the two are kept apart in `stats` because only
one of them is jagt's to make fewer of. The `TOKENS` column and the card show the SUM, which is what a task
actually cost.

Measured floor per call (2026-08): ~25k input tokens of CLI baseline context, ~$0.41 on the inherited default
model vs ~$0.06 on haiku — which is why `orchestrator.assistant.model` **ships as `haiku`** (blank it to
inherit the human's own model). **The lever is fewer calls**, not shorter prompts.
