# The pluggable seams and the assistant

[← AGENTS.md](../../AGENTS.md)

## Pluggable by design

Linux and macOS, with terminals, notifiers, editors
and agent runtimes (any MCP-capable CLI) behind a **strategy interface**: an
implementation plus a config value, never `if claude` or `if macos`.

- Selected by config: `UserNotifier` and `TerminalDriver` by `orchestrator.platform` (macos default, linux;
  kitty the one driver), `EditorDriver` by `orchestrator.editor-command`, `AgentRuntime` and `SubscriptionUsage`
  by `orchestrator.agent.cli` (claude default, codex).
- **A seam selected for the wrong OS is refused at startup, never degraded** (`adapter/PlatformCheck`);
  a notifier reaching nothing logs and returns.
- **The tracker and the code host are not seams of jagt's**: a model reads them through the MCP of whoever
  runs it, jagt holding no credential. Its WORKFLOW is one — `TrackerWorkflow` by
  `orchestrator.tracker.workflow` names the start and close stages, and reads nothing itself.
- `AbstractAgentRuntime` templates `provisionWorktree`, each agent its hook.
- `mcp_client.js` is a **standard, agent-agnostic** stdio↔HTTP proxy; only the config declaring it differs
  (Claude `.mcp.json` + `.claude/settings.local.json`; Codex `.jagt/codex/config.toml`, `CODEX_HOME` pointed
  at it and **not** the worktree's `.codex/`).
- **Nothing outside the runtime names an agent's files**: `WorktreeSetup` calls `provisionWorktree`,
  `AgentSessions` `displayName`.
- **The hook wire is the CLI's own protocol, read where it lands**: `ReadGate` and the two
  gate controllers name its tools and `hookSpecificOutput`, `HostCliLine` the host CLIs.

## Which name holds the briefing is the runtime's to answer

- `AGENTS.md` is `AgentRuntime.SYSTEM_KNOWLEDGE_FILE`; Claude reads `CLAUDE.md`, so its runtime symlinks them.
- Claude's fallback is `CLAUDE.local.md`, the one name a repository does not version; **every other runtime
  refuses**, and the bootstrap prompt names **no** file.
- **A port answers what it achieved, never a value the caller must interpret**: `TerminalDriver.reveal` →
  `Revealed`, `AgentRuntime.lastSessionActivity` → `OptionalLong`, `SubscriptionUsage.read` → read, no
  subscription, unreadable — never a boolean plus a comment or a magic value.
- **A CLI's login is read per usage call, never kept or refreshed**
  ([0033](../decisions/0033-jagt-reads-the-clis-own-login-for-usage-and-keeps-none.md)).
- A read that FAILED is a third thing, the adapter's to report: `ClaudeTranscripts` logs it rather than
  passing a zero up ([never an answer](#a-read-that-failed-is-never-an-answer)).

## Master assistant

The **only** model reading outside jagt, headless and one-shot: ticket, review, intake and routing reads, the
Master's reviewer, the ⌘K palette.

- It can **follow a URL** into a tracker or code host jagt was never told about; never a server
  behind an interactive login or a plugin scope — declared with `orchestrator.assistant.mcp-config` ([one
  command shows which](../installation.md#mcp-access-comes-first)).
- `adapter/assistant/HeadlessClaude`, behind one port per read family, spawns `claude "<prompt>" -p --tools "" --permission-mode
  dontAsk --setting-sources user,project,local --json-schema '<schema>'`, fenced by `ReadGate`, naming **no** MCP server:
  `--setting-sources` inherits the human's **own** MCP, and `java.io.tmpdir` as cwd means only user-level MCP
  loads.
- **Keep `user` in that list**: headless `-p` does not auto-load plugin MCP without it. ~7k tokens buy the
  tracker tools.
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

### An ending says which one

- **A turn, a session, a run or a call ends one of four ways: finished, stopped, failed, gone** — never merged
  in a state, a surface or a log line ([0052](../decisions/0052-an-ending-says-which-one.md)).
- A failure is reported at once with **its source's own word** for the cause (`SessionProbe.State.FAILED`).
- **A seam subscribes to every ending its source emits**, unmatched, and jagt branches on the cause: a hook
  matched to the one value a feature needs leaves the rest to silence (`turn-failure`, not a `rate_limit` matcher).
- An end its source follows at once with a start is no ending (`continuing-end`).
- **Silence is the last verdict, not an outcome**: the watchdog's threshold is for what emits nothing.

## Every assistant call is metered

- `HeadlessClaude` books every call under its kind, **before its answer is judged**, in memory and
  `UsageLedger`; the caller charges the task (`UsageTracker.chargeTask`): a call can precede its task.
- `HeadlessClaude.run` passes every call a spend ceiling, `assistant.max-cost-usd`; a call reaching it fails
  ([0048](../decisions/0048-every-headless-call-has-a-spend-ceiling.md)).
- A sub-agent's own spend is read apart (`AgentSpendReader`, tokens only): `stats` lists both, a
  finished task's record sums them.
- Floor per call: ~25k baseline input tokens, ~$0.41 on the inherited default model against ~$0.06 on
  haiku — hence `orchestrator.assistant.model` **ships as `haiku`**. **The lever is fewer
  calls.**
- **What jagt can read, it reads once and quotes**: `list_tasks` lists, never dumps; the panel's roles share one
  diff ([0009](../decisions/0009-what-jagt-can-read-it-reads-once-and-quotes.md)).
