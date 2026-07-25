# jawo — Multi-Agent Dev Orchestrator

Delegate Jira tickets to Claude Code agents. A Spring Boot backend manages isolated Git worktrees and
task state; you talk to one **Master** session, it spawns one sub-agent per ticket. Agents run as tmux
windows inside a single Warp window that opens automatically.

macOS-only by design: Warp, IntelliJ IDEA, tmux, Claude Code CLI. Your Claude Code must already have
MCP access to the systems the agents need (Jira, GitLab/GitHub).

## Prerequisites

| tool | install |
|------|---------|
| Java 25+ | `sdk install java 25-tem` |
| tmux | `brew install tmux` |
| Node 18+ | `brew install node` |
| Claude Code CLI | claude.com/claude-code |
| Warp | warp.dev |
| IntelliJ IDEA + `idea` CLI | JetBrains Toolbox |
| git | Xcode CLT / brew |

## Setup

```bash
cp config.json.dist config.json   # then fill in your projects
```

Agent tabs are opened via Warp Tab Configs (generated into `~/.warp/tab_configs/`, opened with
`warp://tab_config/<name>`) — no shell hooks needed.

The committed `.claude/settings.json` pre-approves the orchestrator's MCP tools for the Master session
(so tool calls aren't silently blocked). Keep it — see [CLAUDE.md](CLAUDE.md) for the why and the
exact keys.

## Configuration

`config.json` (gitignored, yours — re-read on every access, no restart needed):

| key | meaning |
|-----|---------|
| `projects.<key>.path` | absolute path to the base repository |
| `projects.<key>.baseBranch` | branch new task branches start from, e.g. `origin/main` |
| `projects.<key>.deployBranch` | target of the `deploy` command, e.g. `dev` (omit to disable deploy) |
| `projects.<key>.labels` | hints for mapping tickets to the project |
| `tmuxSession` | name of the agents' tmux session (default `jawo`) |
| `viewMode` | `shared` = all tasks inside one tab; `tab-per-task` = a Warp tab per task |
| `mergeRequestDefaults` | `removeSourceBranch` / `squash` flags for created MRs (default both `true`) |

`orchestrator-backend/src/main/resources/application.yml` (machine/OS level, restart to apply):

| key | meaning |
|-----|---------|
| `orchestrator.platform` | notifier strategy, default `macos` (osascript) |
| `orchestrator.terminal` | terminal driver, default `warp` (tab configs + `warp://tab_config`) |
| `orchestrator.editor-command` | editor launcher list, default `[open, -a, IntelliJ IDEA]`; e.g. `[code]` |
| `orchestrator.claude-command` | agent CLI, default `claude` |
| `orchestrator.agent-prompt` | bootstrap prompt every sub-agent starts with |
| `orchestrator.tmux-command` | tmux binary, default `/opt/homebrew/bin/tmux` |
| `orchestrator.open-warp-window` | auto-open the terminal window (`false` in tests) |
| `orchestrator.watchdog.stale-after` | silence threshold before an "agent unresponsive" alert, default `5m` |
| `orchestrator.root` / `ORCHESTRATOR_ROOT` | override the auto-detected root (nearest dir with `mcp_client.js`) |

## Run

```bash
# tab 1 — backend
cd orchestrator-backend && ./gradlew bootRun

# tab 2 — Master
claude --append-system-prompt "$(cat master_prompt.md)"
```

## Usage`

Tell the Master:

| command | effect |
|---------|--------|
| `do <ticket> [plan] [notes]` | fetch the ticket, spin up a sub-agent in an isolated worktree; `plan` = plan mode |
| `status` | dashboard only |
| `feedback <ticket> <text>` | relay a note/correction to the running sub-agent |
| `respawn <ticket>` | restart a sub-agent session for an already-registered task |
| `done <ticket>` | close the task at any stage: full cleanup — window, worktree, state (branch kept) |
| `focus <ticket>` | jump to the task's agent window (tmux window + Warp to front) |
| `ide <ticket>` | your review checkpoint: open the worktree in IntelliJ (after agent's commit and after each round) |
| `ci <ticket>` | check the pipeline for the task branch and update status |
| `deploy <ticket>` | merge the task branch into the project's `deployBranch` and push (conflicts → you) |
| `ship <ticket>` | review approved: commit as "<id>: <jira title>", push, open MR, watch CI |
| `review <ticket>` | collect MR review round: agent fixes locally + drafts replies; nothing is pushed |
| `help` | command reference + recovery cheatsheet |

Every Master reply ends with the task dashboard. Agents live in one Warp window: click a task name in
the tmux status bar (or `Ctrl+b w`) to switch. Plain-text status any time: `curl localhost:8080/status`.

## The ideal flow (commands in order)

The Master validates the command order against the task status (override with "force"). Dashed = optional.

```mermaid
flowchart TD
    DO["do ABC-123 [plan]"]
    IDE1["ide ABC-123"]
    FB["feedback ABC-123 ..."]
    SHIP["ship ABC-123"]
    REVIEW["review ABC-123"]
    IDE2["ide ABC-123"]
    CI["ci ABC-123"]
    FB2["feedback ABC-123 ..."]
    DEPLOY["deploy ABC-123"]
    DONE["done ABC-123"]

    FOCUS["focus ABC-123"]
    DO -.->|"optional: watch the agent live"| FOCUS
    FOCUS -.-> IDE1
    DO -->|"agent works — no commits"| IDE1
    IDE1 -->|"needs changes"| FB
    FB -->|"agent reworks"| IDE1
    IDE1 -->|"approved"| SHIP
    SHIP -->|"commit with the Jira title, push, MR, replies posted"| CI
    CI -->|"failed"| FB
    CI -->|"green, unresolved comments"| REVIEW
    REVIEW -->|"agent fixes locally, drafts replies — no push"| IDE1
    CI -->|"green, all resolved"| DEPLOY
    DEPLOY -->|"merged into deployBranch, pushed (conflicts → you)"| DONE

    classDef cmd font-family:monospace,fill:#1a1a2e,color:#7ee787,stroke:#7ee787;
    class DO,FOCUS,IDE1,FB,SHIP,REVIEW,CI,DEPLOY,DONE cmd;
```

Notes: `[plan]` — agent plans first, you approve in its window. Between commands the agent does the
actual work (arrow labels). Every task gets a short alias (`p1`, `s2`) shown in the dashboard — use it
in any command instead of the ticket id. Any time: `status`, `focus <ticket>` (respawns the session if
it died), `respawn <ticket>`, `help`. Closing the Warp window only detaches the viewer — agents keep
running; kill sessions explicitly with `done`.

## Your role (the human in the loop)

The system never acts on the MR/CI by itself — three checkpoints are explicitly yours
(also the roadmap for future automation):

| # | role | when | command |
|---|------|------|---------|
| A | code review | agent committed (REVIEW_PENDING) and after every review round | `ide` → then `feedback` or `ship` |
| B | CI/CD + review progress | after `ship` (nothing polls automatically) | `ci`, `review` |
| C | closing the loop | CI green, reviewers satisfied | `done` (full cleanup) |

## Internals

Architecture, fault-tolerance guarantees and engineering constraints: see [CLAUDE.md](CLAUDE.md).
