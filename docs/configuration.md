# Configuration

[← README](../README.md)

Two files:

| file | holds | applied |
|------|-------|---------|
| `config.json` | your projects and how you work | re-read on every access — no restart |
| `orchestrator-backend/src/main/resources/application.yml` | machine and OS settings | on restart |

`config.json` is gitignored. Copy it from `config.json.dist` and never commit your own paths.

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
| `orchestrator.platform` | `macos` | `macos` or `linux`; selects the notifier and kitty driver |
| `server.address` | `127.0.0.1` | which interface the board listens on |
| `server.port` | `8290` | the board's port |
| `orchestrator.notify-send-command` | `notify-send` | Linux only |

### Terminal and editor

| key | default | meaning |
|-----|---------|---------|
| `orchestrator.terminal` | `kitty` | `kitty` or `warp`; both run over tmux |
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
| `orchestrator.stub.script` | — | only for `orchestrator.agent=stub` |

### Code host and tracker

Both optional and **read-only**. Set them and reads cost an API call instead of a model call.

| key | meaning |
|-----|---------|
| `orchestrator.code-host.type` | `gitlab`, `github`, or blank = off (default) |
| `orchestrator.code-host.base-url` | the host's **web** root, e.g. `https://gitlab.example.com` |
| `orchestrator.code-host.token` | read-only API token, e.g. `${CODE_HOST_TOKEN}` |
| `orchestrator.tracker.type` | `jira`, or blank = off (default) |
| `orchestrator.tracker.base-url` | the tracker root, e.g. `https://tracker.example.com` |
| `orchestrator.tracker.user` | the account a token belongs to (Jira Cloud); blank for a self-hosted PAT |
| `orchestrator.tracker.token` | read-only API token, e.g. `${TRACKER_TOKEN}` |

### Master assistant

The one place jagt spends model money — a headless one-shot read, used when no tracker or code host covers it.

| key | default | meaning |
|-----|---------|---------|
| `orchestrator.assistant.model` | `haiku` | ≈$0.06 a call vs ≈$0.41 on the inherited default; blank = yours |
| `orchestrator.assistant.setting-sources` | `user,project,local` | which MCP and settings the read inherits |
| `orchestrator.assistant.permission-mode` | `bypassPermissions` | lets the headless read call MCP at all |
| `orchestrator.assistant.allowed-tools` | *(empty)* | `mcp__<server>` allow-list; scopes the bypass |
| `orchestrator.assistant.mcp-config` | *(empty)* | declare the servers instead of inheriting them |

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

**`code-host.base-url` is the web root, not the API root.** A review URL is only read under this prefix, and
the API endpoints are derived from it — github.com serves its API from another host, an Enterprise install
from its own.

**`assistant.mcp-config` buys determinism, not money.** Measured cold it cost $0.09 against $0.04 for the
inherited config, which rides the prompt cache your own sessions keep warm. Server names also lose their
plugin scope here (`mcp__gitlab__…`, not `mcp__plugin_<x>_gitlab__…`), so an `allowed-tools` list written for
the inherited names silently stops matching.

**Binaries are resolved by bare name** — PATH first, then the usual install directories (Homebrew included, a
GUI-launched process has neither on PATH), then inside application bundles. Give an absolute path to pin one.
For `editor-command` only the launcher is resolved; the arguments stay yours.

**`startup-checks`.** Every problem is reported at once, each line naming the key that fixes it. Nothing
reaches the network, so a token that is present but wrong surfaces at the first read instead. Test suites and
smoke scripts pass `false`: what is checked is your machine, and a CI runner is not one.
