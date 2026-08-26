# Configuration

[← README](../README.md)

`config.json` holds your projects and how you work; it is re-read on every access, so nothing restarts. It is
gitignored — copy it from `config.json.dist` and never commit your own paths.

Everything else is a Spring setting, and those come from **three** places. Later wins, and only the middle one
is yours to edit:

| | where | holds | committed |
|---|-------|-------|-----------|
| 1 | `orchestrator-backend/src/main/resources/application.yml` | the defaults, and the comment on every key | yes — it is built INTO the jar |
| 2 | `orchestrator-backend/config/application.yml` | **your machine** | no, gitignored |
| 3 | a flag or a variable on the command line | one run | no |

**A setting of yours goes in (2).** Spring Boot reads `./config/application.yml` from the directory it starts
in, which is `orchestrator-backend` — and it overrides the packaged file key by key, so you write only what you
change. It survives every `./gradlew build`, and it never lands in a commit.

(1) is the reference: every key jagt has, with why it is set that way. Editing it puts your machine into the
repository and needs a rebuild to take effect. (3) is for one run — `--orchestrator.ui=tui`, `LOG_FILE=…` — and
outranks both.

Applied on restart, all three: the jar reads them once at startup.

## Projects

The only section you must fill in.

```json
{
  "projects": {
    "sng": {
      "path": "/Users/you/work/widget-service",
      "baseBranch": "origin/main",
      "deployBranch": "dev",
      "labels": ["widget", "backend"]
    }
  }
}
```

| key | meaning |
|-----|---------|
| `path` | absolute path to the base repository |
| `baseBranch` | where task branches are cut from. **Read-only** — jagt never pushes here |
| `deployBranch` | target of `deploy`. Omit to disable deploy for this project |
| `labels` | hints for mapping a ticket to this project |

## config.json

Any section may be omitted — each key falls back to its default.

### viewer

| key | default | meaning |
|-----|---------|---------|
| `tmuxSession` | `jagt` | name of the agents' tmux session |
| `viewMode` | `shared` | `shared` = one tab for all tasks; `tab-per-task` = one per task |
| `keepViewer` | `true` | keep the agents window open after the last task |

### dashboard

| key | default | meaning |
|-----|---------|---------|
| `refreshSeconds` | `10` | how often the console dashboard refreshes |
| `reservedRows` | `17` | rows kept for output and input below the dashboard |

### codeReview

| key | default | meaning |
|-----|---------|---------|
| `mrTitlePattern` | `{ticket} {title}` | request and commit title template |
| `postReviewReplies` | `true` | on `ship`, post the agent's drafted replies to the threads |
| `reviewReplyAuthors` | `[]` | post replies only to threads by these authors; empty = all |
| `mergeRequestDefaults` | both `true` | `removeSourceBranch` and `squash` on created requests |

### autoReview

Off by default. When on, jagt polls every task that has an open review request — approval advances the task,
comments are drafted for you. **It never posts, pushes or deploys.**

| key | default | meaning |
|-----|---------|---------|
| `enabled` | `false` | poll open review requests |
| `windowHours` | `24` | how long polling runs per round, then it pings you to `sweep` |
| `minIntervalMinutes` | `10` | interval at the start of the window |
| `maxIntervalMinutes` | `60` | interval at the end; it ramps linearly between the two |

### agent, worktree

| key | default | meaning |
|-----|---------|---------|
| `agent.outputStyle` | `""` | output style for the agent CLI; empty = the agent's own |
| `agent.probeSeconds` | `600` | how often every running session is looked at; a session whose hooks report needs no wait |
| `worktree.copyGlobs` | `["**/.env"]` | gitignored local files copied into each worktree |

## application.yml

### Surfaces and platform

| key | default | meaning |
|-----|---------|---------|
| `orchestrator.ui` | `web` | `web`, `tui` or `both` |
| `orchestrator.platform` | `macos` | `macos` or `linux`; selects the notifier and kitty driver. Refused when it is not what the machine reports |
| `server.address` | `127.0.0.1` | which interface the board listens on |
| `server.port` | `8290` | the board's port |
| `orchestrator.notify-send-command` | `notify-send` | Linux only |
| `orchestrator.terminal-notifier-command` | `terminal-notifier` | macOS only; a build that refuses the banner falls back to osascript |

### Terminal and editor

| key | default | meaning |
|-----|---------|---------|
| `orchestrator.terminal` | `kitty` | `kitty` or `warp`; both run over tmux. `warp` is macOS-only — it is opened through a URI scheme |
| `orchestrator.kitty-command` | `kitty` | the kitty binary |
| `orchestrator.kitty-font-size` | *(blank)* | blank keeps kitty.conf's own |
| `orchestrator.tmux-command` | `tmux` | the tmux binary |
| `orchestrator.editor-command` | `[idea]` | editor launcher, e.g. `[code]` |
| `orchestrator.editor-diff-command` | `[idea, diff]` | difftool for `ide <ticket> diff`, e.g. `[difft]` |
| `orchestrator.open-warp-window` | `true` | auto-open the agents terminal window |

### Web terminal

Shows the agent's session inside the board when you press Focus. Needs ttyd installed.

| key | default | meaning |
|-----|---------|---------|
| `orchestrator.web-terminal.enabled` | `false` | turn it on |
| `orchestrator.web-terminal.command` | `ttyd` | the ttyd binary |
| `orchestrator.web-terminal.port` | `8291` | where it starts looking for a free port |
| `orchestrator.web-terminal.bind` | `127.0.0.1` | which interface it listens on |

### Agent runtime

| key | default | meaning |
|-----|---------|---------|
| `orchestrator.agent` | `claude` | `claude`, `codex` (or `stub` for the e2e matrix) |
| `orchestrator.claude.command` | `claude` | the Claude Code binary |
| `orchestrator.codex.command` | `codex` | the Codex binary |
| `orchestrator.agent-prompt` | *(built in)* | bootstrap prompt every sub-agent starts with |
| `orchestrator.agent-disabled-plugins` | *(empty)* | plugins disabled per agent worktree |
| `orchestrator.mcp-url` | `http://localhost:<port>/mcp` | where an agent reaches jagt |
| `orchestrator.hook-url` | `http://127.0.0.1:<port>/api/agent/session` | where an agent CLI's hooks report a stopped session |
| `orchestrator.gate-url` | `http://127.0.0.1:<port>/api/agent` | where a session asks before it pushes |
| `orchestrator.stub.script` | — | only for `orchestrator.agent=stub` |

### Master assistant

The one place jagt spends model money, and the only way it reads a ticket or a review round: a headless
one-shot read through the MCP servers of whoever runs jagt.

| key | default | meaning |
|-----|---------|---------|
| `orchestrator.assistant.model` | `haiku` | ≈$0.06 a call vs ≈$0.41 on the inherited default; blank = yours |
| `orchestrator.assistant.setting-sources` | `user,project,local` | which MCP and settings the read inherits |
| `orchestrator.assistant.permission-mode` | `bypassPermissions` | lets the headless read call MCP at all |
| `orchestrator.assistant.allowed-tools` | *(empty)* | `mcp__<server>` allow-list; scopes the bypass |
| `orchestrator.assistant.mcp-config` | *(empty)* | declare the servers instead of inheriting them |

Inheriting is the default and usually right. **Declare instead when the servers that can answer are
plugin-scoped**: a headless `-p` read loads none of those, so an install whose tracker and code-host servers
come from a plugin has NO tool for either — and the read fails naming whichever unauthenticated connector it
could still see, which is not the one at fault. `claude mcp list` shows the plugin server as connected the
whole time, so nothing looks wrong. Point the key at a servers file (the plugin's own `.mcp.json` will do —
its `${VAR}` placeholders resolve from the environment the backend was started in) and the read gets exactly
those and nothing else. Details: [seams](rules/seams.md).

### Paths and safety

| key | default | meaning |
|-----|---------|---------|
| `orchestrator.startup-checks` | `true` | refuse to start when the installation is incomplete |
| `orchestrator.watchdog.stale-after` | `5m` | silence before an "agent stopped" alert; a hook report needs no wait |
| `orchestrator.config-file` | *(root)* | where `config.json` lives |
| `orchestrator.state-file` | *(root)* | where `state.json` lives |
| `orchestrator.root` / `ORCHESTRATOR_ROOT` | *(auto)* | override the detected orchestrator root |

## Notes

**`worktree.copyGlobs`.** Run configs reference module `.env` files, keys and certificates that are gitignored
and therefore missing from a fresh worktree, so the app would not start. Every copy is another copy of a
credential in a sibling directory — widen this yourself, per project, to what your run configs actually need.
A `**/` prefix also matches at the repository root, so `**/.env` covers both `app/.env` and a single-module
repo's own `.env`.

**`server.address`.** The board asks for no password and can deploy, close a task and start an agent, so it
stays on loopback until you decide otherwise. Client defaults use `127.0.0.1` rather than `localhost`, which
resolves `::1` first and would cost a refused connection per call.

**`web-terminal.bind`.** The terminal is writable, so reaching it is reaching a shell. Only the page ttyd
serves may open a socket into the session (`--check-origin`); this key decides who may ask for that page at
all. Widen it only on a network you trust. The panel always asks for that port on the host you opened the
board under, so a board opened from a second machine needs a bind that machine can reach.

**`assistant.mcp-config` buys determinism, not money.** Measured cold it cost $0.09 against $0.04 for the
inherited config, which rides the prompt cache your own sessions keep warm. Server names also lose their
plugin scope here (`mcp__gitlab__…`, not `mcp__plugin_<x>_gitlab__…`), so an `allowed-tools` list written for
the inherited names silently stops matching.

**`CLAUDE_CONFIG_DIR` is read from jagt's own environment**, not from the session's. Exporting it in one shell
and starting jagt from another loses the derived path to a session's log; a session that reports the file it
appends to is unaffected either way.

**Binaries are resolved by bare name** — PATH first, then the usual install directories (Homebrew included, a
GUI-launched process has neither on PATH), then inside application bundles. Give an absolute path to pin one.
For `editor-command` only the launcher is resolved; the arguments stay yours.

**`startup-checks`.** Every problem is reported at once, each line naming the key that fixes it. Nothing
reaches the network, so a token that is present but wrong surfaces at the first read instead. Test suites and
smoke scripts pass `false`: what is checked is your machine, and a CI runner is not one.
