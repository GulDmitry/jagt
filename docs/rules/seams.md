# The pluggable seams and the assistant

[← AGENTS.md](../../AGENTS.md)

## Pluggable by design

**A firm architectural invariant. Do not erode it.** Linux and macOS both, with terminals, notifiers, editors
and agent runtimes (any MCP-capable CLI) behind a **strategy interface** selected by config: an
implementation plus a config value, never `if claude` or `if macos`. The flow stays agent-agnostic.

- Selected by config: `UserNotifier` and `TerminalDriver` by `orchestrator.platform` (macos default, linux;
  kitty the one driver), `EditorDriver` by `orchestrator.editor-command`, `AgentRuntime` by
  `orchestrator.agent.cli` (claude default, codex).
- **A seam selected for the wrong OS is refused at startup, never degraded** (`adapter/PlatformCheck`) —
  except that a notifier reaching nothing logs and returns.
- **The tracker and the code host are not seams of jagt's**: a model reads them through the MCP of whoever
  runs it, jagt holding no credential.
- `AgentRuntime` covers `launchCommand`, `provisionWorktree` (template in `AbstractAgentRuntime` + per-agent
  hook) and `lastSessionActivity`.
- `mcp_client.js` is a **standard, agent-agnostic** stdio↔HTTP proxy; only the config declaring it differs
  (Claude `.mcp.json` + `.claude/settings.local.json`; Codex `.jagt/codex/config.toml`, `CODEX_HOME` pointed
  at it and **not** the worktree's `.codex/`).
- **Nothing outside the runtime names an agent's files**: `WorktreeSetup` calls `provisionWorktree`,
  `AgentSessions` `displayName`.

## Which name holds the briefing is the runtime's to answer

- `AGENTS.md` is `AgentRuntime.SYSTEM_KNOWLEDGE_FILE`; Claude reads `CLAUDE.md`, so its runtime symlinks one
  to the other.
- Claude's fallback is `CLAUDE.local.md`, the one name a repository does not version; **every other runtime
  refuses**, and the bootstrap prompt names **no** file.
- **A port answers what it achieved, never a value the caller must interpret**: `TerminalDriver.reveal` →
  `Revealed` (`WINDOW`, `UNREACHABLE_TAB`, `NOT_RUNNING`), `AgentRuntime.lastSessionActivity` →
  `OptionalLong` — never a boolean plus a comment, never a magic value.
- A read that FAILED is a third thing, the adapter's to report: `ClaudeTranscripts` logs it rather than
  passing a zero up ([never an answer](#a-read-that-failed-is-never-an-answer)).

## Master assistant

The **only** way jagt reads outside itself, headless and one-shot: `service/TicketReader`,
`service/ReviewReader`, the ⌘K palette.

- It can **follow a URL** into a tracker or code host jagt was never told about; it cannot reach a server
  behind an interactive login or a plugin scope — declared with `orchestrator.assistant.mcp-config` ([one
  command shows which](../installation.md#mcp-access-comes-first)).
- `HeadlessClaudeAssistant` (`MasterAssistant`) spawns `claude "<prompt>" -p --setting-sources
  user,project,local --json-schema '<schema>'` through `ProcessRunner`, hardcoding **no** MCP server or path:
  `--setting-sources` inherits the human's **own** MCP, and `java.io.tmpdir` as cwd means only user-level MCP
  loads.
- **Keep `user` in that list**: headless `-p` does not auto-load plugin MCP without it, and `project` alone
  resolves to **zero** servers from the temp dir. Its ~7k tokens buy the tracker tools.
- An install may **declare** the servers instead (`assistant.mcp-config` → `--strict-mcp-config`, `${ENV}`
  placeholders so jagt holds no credential): a **determinism knob only**, $0.09 cold against $0.04.

### A read that failed is never an answer

- **"I could not look" and "there is no such thing" are two different answers**, and jagt must never merge
  them.
- Every read's schema carries a **`failure`** string, empty **only** when the host itself answered; whatever
  stopped the read goes there, naming the tool or server.
- A non-empty `failure` comes back as **empty facts** (unreadable), logged at ERROR; it never becomes
  `exists=false`.
- On an unreadable read the callers ask `brokenMcpServers()` (`adapter/assistant/McpHealthProbe`,
  `claude mcp list`): **three** values — down / nothing down / **could not be established** — and collapsing
  the last two is the same bug one layer down.
- The surfaces say which happened: **never "could not read (or not found)"**.

## Every assistant call is metered

- `UsageTracker` books a call to the task (`state.json`) and the session (in memory), **before its answer is
  judged**.
- A sub-agent's own spend is read separately (`AgentSpendReader`, tokens only); `TOKENS` and the card show
  the SUM.
- Floor per call: ~25k input tokens of baseline, ~$0.41 on the inherited default model against ~$0.06 on
  haiku — hence `orchestrator.assistant.model` **ships as `haiku`** (blank to inherit). **The lever is fewer
  calls.**
