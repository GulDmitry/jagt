# The pluggable seams and the assistant

[← AGENTS.md](../../AGENTS.md)

## Pluggable by design

Linux and macOS both, with terminals, notifiers, editors
and agent runtimes (any MCP-capable CLI) behind a **strategy interface**: an
implementation plus a config value, never `if claude` or `if macos`.

- Selected by config: `UserNotifier` and `TerminalDriver` by `orchestrator.platform` (macos default, linux;
  kitty the one driver), `EditorDriver` by `orchestrator.editor-command`, `AgentRuntime` by
  `orchestrator.agent.cli` (claude default, codex).
- **A seam selected for the wrong OS is refused at startup, never degraded** (`adapter/PlatformCheck`);
  a notifier reaching nothing logs and returns.
- **The tracker and the code host are not seams of jagt's**: a model reads them through the MCP of whoever
  runs it, jagt holding no credential. Its WORKFLOW is one — `TrackerWorkflow` by
  `orchestrator.tracker.workflow` names the start and close stages, and reads nothing itself.
- `AgentRuntime` covers `launchCommand`, `provisionWorktree` (template in `AbstractAgentRuntime` + per-agent
  hook) and `lastSessionActivity`.
- `mcp_client.js` is a **standard, agent-agnostic** stdio↔HTTP proxy; only the config declaring it differs
  (Claude `.mcp.json` + `.claude/settings.local.json`; Codex `.jagt/codex/config.toml`, `CODEX_HOME` pointed
  at it and **not** the worktree's `.codex/`).
- **Nothing outside the runtime names an agent's files**: `WorktreeSetup` calls `provisionWorktree`,
  `AgentSessions` `displayName`.
- **The hook wire is the CLI's own protocol, read where it lands**: `ToolGate`, `ReadGate` and
  their two controllers name its tools and `hookSpecificOutput`, `HostCliLine` the host CLIs.

## Which name holds the briefing is the runtime's to answer

- `AGENTS.md` is `AgentRuntime.SYSTEM_KNOWLEDGE_FILE`; Claude reads `CLAUDE.md`, so its runtime symlinks them.
- Claude's fallback is `CLAUDE.local.md`, the one name a repository does not version; **every other runtime
  refuses**, and the bootstrap prompt names **no** file.
- **A port answers what it achieved, never a value the caller must interpret**: `TerminalDriver.reveal` →
  `Revealed`, `AgentRuntime.lastSessionActivity` → `OptionalLong` — never a boolean plus a comment or a magic value.
- A read that FAILED is a third thing, the adapter's to report: `ClaudeTranscripts` logs it rather than
  passing a zero up ([never an answer](#a-read-that-failed-is-never-an-answer)).

## Master assistant

The **only** way jagt reads outside itself, headless and one-shot: ticket, review, intake and routing reads, the
Master's reviewer, the ⌘K palette.

- It can **follow a URL** into a tracker or code host jagt was never told about; never a server
  behind an interactive login or a plugin scope — declared with `orchestrator.assistant.mcp-config` ([one
  command shows which](../installation.md#mcp-access-comes-first)).
- `adapter/assistant/HeadlessClaude`, behind one port per read family, spawns `claude "<prompt>" -p --tools "" --permission-mode
  dontAsk --setting-sources user,project,local --json-schema '<schema>'`, fenced by `ReadGate`, naming **no** MCP server:
  `--setting-sources` inherits the human's **own** MCP, and `java.io.tmpdir` as cwd means only user-level MCP
  loads.
- **Keep `user` in that list**: headless `-p` does not auto-load plugin MCP without it, and `project` alone
  resolves to **zero** servers from the temp dir. ~7k tokens buy the tracker tools.
- An install may **declare** the servers instead (`assistant.mcp-config` → `--strict-mcp-config`, `${ENV}`
  placeholders so jagt holds no credential): a **determinism knob only**, $0.09 cold against $0.04.

### A read that failed is never an answer

- **"I could not look" and "there is no such thing" are two answers, never merged.**
- Every read's schema carries a **`failure`** string, empty **only** when the host itself answered,
  else naming the tool or server that stopped it.
- A non-empty `failure` is **empty facts** (unreadable), logged at ERROR, never `exists=false`.
- On an unreadable read the callers ask `McpHealth` (`adapter/assistant/ClaudeMcpHealthProbe`,
  `claude mcp list`): **three** values — down / nothing down / **could not be established**; merging
  the last two is the same bug.
- The surfaces say which happened: **never "could not read (or not found)"**.

## Every assistant call is metered

- `HeadlessClaude` books every call to the session under its kind, **before its answer is judged**; the
  caller charges the task (`UsageTracker.chargeTask`), as a call can precede its task.
- A sub-agent's own spend is read apart (`AgentSpendReader`, tokens only): `stats` lists both, a
  finished task's record sums them.
- Floor per call: ~25k baseline input tokens, ~$0.41 on the inherited default model against ~$0.06 on
  haiku — hence `orchestrator.assistant.model` **ships as `haiku`** (blank to inherit). **The lever is fewer
  calls.**
- **What jagt can read, it reads once and quotes**: `list_tasks` lists, never dumps; the panel's roles share one
  diff ([0009](../decisions/0009-what-jagt-can-read-it-reads-once-and-quotes.md)).
