# Configuration

[← README](../README.md)

Everything is in **one file**: `jagt.yml` at the repository root — `cp jagt.yml.dist jagt.yml`. The dist file
carries every key with what it means; your copy is gitignored, so comment it freely.

One root, `orchestrator:`, holds all of it. jagt re-reads `projects` on **every access**; Spring binds the rest
**once at startup**. An omitted key falls back to the defaults built into
`orchestrator-backend/src/main/resources/application.yml`. A flag or environment variable
(`--server.port=8390`, `LOG_FILE=…`) outranks both files, for one run.

> [!NOTE]
> `config.json` is no longer read. If one is still lying around, jagt refuses to start and prints the `jagt.yml`
> to write in its place.

## Projects

The only section with no default.

```yaml
orchestrator:
  projects:
    widgets:
      path: /Users/you/work/widget-service
      baseBranch: origin/main
      deployBranch: dev
      labels: [widget, backend]
```

| key | meaning |
|-----|---------|
| `path` | absolute path to the base repository |
| `baseBranch` | where task branches are cut from. **Read-only**: jagt never pushes here |
| `deployBranch` | target of `deploy`; omit to disable deploy here |
| `labels` | hints for mapping a ticket to this project |

## Re-read while jagt runs

Read on every access, so an edit lands without a restart. Any key may be omitted.

| key | default | meaning |
|-----|---------|---------|
| `viewer.tmuxSession` | `jagt` | name of the agents' tmux session |
| `viewer.viewMode` | `shared` | one tab for all tasks, or `tab-per-task` |
| `viewer.keepViewer` | `true` | keep the agents window open after the last task |
| `codeReview.mrTitlePattern` | `{ticket} {title}` | request and commit title template |
| `codeReview.postReviewReplies` | `true` | on `ship`, post the drafted replies to the threads |
| `codeReview.reviewReplyAuthors` | `[]` | reply only to threads by these authors; empty = all |
| `codeReview.mergeRequestDefaults` | both `true` | `removeSourceBranch` and `squash` on created requests |
| `autoReview.enabled` | `false` | poll open review requests |
| `autoReview.windowHours` | `24` | polling per round, then it pings you to `sweep` |
| `autoReview.minIntervalMinutes` | `10` | interval at the start of the window |
| `autoReview.maxIntervalMinutes` | `60` | interval at the end; it ramps linearly |
| `agent.outputStyle` | `""` | output style for the agent CLI; empty = the agent's own |
| `agent.probeSeconds` | `600` | how often a session is probed; one whose hooks report needs no wait |
| `worktree.copyGlobs` | `["**/.env"]` | gitignored local files copied into each worktree |

Auto-review polls every task with an open review request: approval advances it, comments are drafted for you.
**It never posts, pushes or deploys.**

## Read once, at startup

### Board, platform, terminal and editor

| key | default | meaning |
|-----|---------|---------|
| `orchestrator.platform` | `macos` | `macos` or `linux`; picks the notifier and kitty driver. Refused when the machine reports otherwise |
| `server.address` | `127.0.0.1` | which interface the board listens on |
| `server.port` | `8290` | the board's port |
| `orchestrator.notify-send-command` | `notify-send` | Linux only |
| `orchestrator.terminal-notifier-command` | `terminal-notifier` | macOS only; a build that refuses the banner falls back to osascript |
| `orchestrator.kittyCommand` | `kitty` | the kitty binary |
| `orchestrator.kitty-font-size` | *(blank)* | blank keeps kitty.conf's own |
| `orchestrator.tmuxCommand` | `tmux` | the tmux binary |
| `orchestrator.editorCommand` | `[idea]` | editor launcher, e.g. `[code]` |
| `orchestrator.editor-diff-command` | `[idea, diff]` | difftool for `ide <ticket> diff`, e.g. `[difft]` |
| `orchestrator.open-terminal-window` | `true` | auto-open the agents terminal window |

### Agent runtime

| key | default | meaning |
|-----|---------|---------|
| `orchestrator.agent.cli` | `claude` | `claude`, `codex` (or `stub` for the e2e matrix) |
| `orchestrator.claude.command` | `claude` | the Claude Code binary |
| `orchestrator.codex.command` | `codex` | the Codex binary |
| `orchestrator.agentPrompt` | *(built in)* | bootstrap prompt every sub-agent starts with |
| `orchestrator.agentDisabledPlugins` | *(empty)* | plugins disabled per agent worktree |
| `orchestrator.mcpUrl` | `http://127.0.0.1:<port>/mcp` | where an agent reaches jagt |
| `orchestrator.hookUrl` | `http://127.0.0.1:<port>/api/agent/session` | where a CLI's hooks report a stopped session |
| `orchestrator.gateUrl` | `http://127.0.0.1:<port>/api/agent` | where a session asks before it pushes |
| `orchestrator.stub.script` | — | only for `orchestrator.agent.cli=stub` |

### Master assistant

The one place jagt spends model money, and the only way it reads a ticket, a merge request or a review round:
a headless one-shot read through the MCP servers of whoever runs jagt.

| key | default | meaning |
|-----|---------|---------|
| `orchestrator.assistant.model` | `haiku` | blank inherits your own; costs in [seams](rules/seams.md) |
| `orchestrator.assistant.setting-sources` | `user,project,local` | which MCP and settings the read inherits |
| `orchestrator.assistant.permission-mode` | `bypassPermissions` | lets the headless read call MCP at all |
| `orchestrator.assistant.allowed-tools` | *(empty)* | `mcp__<server>` allow-list; scopes the bypass |
| `orchestrator.assistant.mcp-config` | *(empty)* | declare the servers instead of inheriting them |

Inheriting is the default. **Declare instead when the servers that can answer are plugin-scoped or need an
interactive login** — a headless read has neither, and the command that shows it is in
[Installation](installation.md#mcp-access-comes-first).

### Paths and safety

| key | default | meaning |
|-----|---------|---------|
| `orchestrator.startupChecks` | `true` | refuse to start when the installation is incomplete |
| `orchestrator.watchdog.stale-after` | `5m` | silence before an "agent stopped" alert; a hook report needs no wait |
| `orchestrator.configFile` | *(root)* | where `jagt.yml` lives — flag or env only, being read before it |
| `orchestrator.stateFile` | *(root)* | where `state.json` lives |
| `orchestrator.root` / `ORCHESTRATOR_ROOT` | *(auto)* | override the detected orchestrator root |

## Notes

**`worktree.copyGlobs`** is one top-level list for every project. Widen it to what your run configs need,
knowing each copy is another copy of a credential in a sibling directory. `**/` also matches at the repository
root, so `**/.env` covers both `app/.env` and a single-module repo's own `.env`.

**`server.address`** stays on loopback until you decide otherwise. Clients default to `127.0.0.1` rather than
`localhost`, which resolves `::1` first and would cost a refused connection per call.

**`assistant.mcp-config` buys determinism, not money** — figures in [seams](rules/seams.md). Server names lose
their plugin scope here (`mcp__gitlab__…`, not `mcp__plugin_<x>_gitlab__…`), so an `allowed-tools` list written
for the inherited names stops matching.

**`CLAUDE_CONFIG_DIR` is read from jagt's own environment**, not the session's: exporting it in one shell and
starting jagt from another loses the derived path to a session's log.

**Binaries are resolved by bare name** — PATH, then the usual install directories (a GUI-launched process has
no Homebrew on PATH), then inside application bundles. Give an absolute path to pin one; for `editorCommand`
only the launcher is resolved, and the arguments stay yours.

**`startupChecks`.** What is missing is [said at startup, all of it at
once](rules/components.md#what-is-missing-is-said-at-startup-not-at-the-click-that-needed-it). Nothing reaches
the network, so a token that is present but wrong surfaces at the first read instead. Test suites and smoke
scripts pass `false`: what is checked is your machine, and a CI runner is not one.
