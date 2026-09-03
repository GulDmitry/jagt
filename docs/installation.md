# Installation

[← README](../README.md)

## MCP access comes first

jagt talks to no external service: it reads a ticket, and a review round, by spawning a **headless** one-shot
of your agent CLI, which uses **your** MCP servers. Two kinds of server cannot answer such a call:

- one that only an **interactive login** authenticates — a headless session authenticates none;
- one that is **plugin-scoped** — a headless session does not load those at all.

`claude mcp list` calls a plugin server connected throughout, so nothing looks wrong: the read has no tool for
the host it was asked about, and fails naming whichever unauthenticated server it could still see. Check what a
headless call actually has:

```sh
cd "$TMPDIR" && claude "Name your MCP tools for <your tracker> and <your code host>, or say NONE." -p
```

`NONE` means jagt cannot read a ticket. Point it at a servers file instead — a plugin's own `.mcp.json` will
do, its `${VAR}` placeholders resolving from the environment the backend was started in:

```yaml
# jagt.yml
orchestrator:
  assistant:
    mcpConfig: /path/to/mcp-servers.json
```

That also cuts each read to the servers it needs, which is most of what one costs. Declare the tracker and
the code host and stop there: past a few hundred loaded tools the CLI hands the read a tool SEARCH instead of
the tools, and a read spends its turns looking for the one it already named.

A declared server logs in as itself: a login you did through the plugin shipping the same file does not count.
Authenticate it once against that file, and the headless read finds the token under the same server name:

```sh
claude --strict-mcp-config --mcp-config /path/to/mcp-servers.json --setting-sources user,project,local
# /mcp -> the server -> Authenticate
```

## Prerequisites

| tool | macOS | Linux | needed for |
|------|-------|-------|------------|
| Java 25+ | `sdk install java 25-tem` | `sdk install java 25-tem` | the backend |
| an agent CLI | [Claude Code](https://claude.com/claude-code), or the Codex CLI | same | the agents |
| tmux | `brew install tmux` | `apt install tmux` | persistent agent sessions |
| git | Xcode CLT or `brew install git` | `apt install git` | worktrees |
| kitty | `brew install kitty` | `apt install kitty` | the agents terminal |
| an editor | IntelliJ IDEA via JetBrains Toolbox | `idea` or `code` on PATH | the `ide` checkpoint |
| a notifier | `brew install terminal-notifier` | `apt install libnotify-bin` | desktop notifications, clickable on macOS |
| a notification daemon | — | your desktop's own, or `apt install dunst` | showing them; GNOME and KDE bring one, a bare WM does not |
| lsof | — | `apt install lsof` | reaping a worktree's leftover processes on `done`; skipped when absent |
| Node 18+ | `brew install node` | `apt install nodejs` | only for `orchestrator.agent.cli=codex` |

## Linux

```yaml
orchestrator:
  platform: linux         # selects the notifier and the kitty driver
  editorCommand: [idea]   # or [code]
```

`platform` is not detected: it defaults to `macos`, and jagt refuses to start when it is not what the machine
reports. Everything else is shared with macOS — kitty speaks the same remote-control protocol on both — bar
one difference: **a Linux banner does not open the board.** `notify-send` carries a click only by staying alive
waiting for the daemon, which a fire-and-forget notification cannot; the task is named in the title instead.

## Run it

```bash
cp jagt.yml.dist jagt.yml
cd orchestrator-backend
./gradlew build stageJar
java -jar build/libs/jagt-run.jar
```

Add at least one project to `jagt.yml` — see [Configuration](configuration.md). If something is missing or
half-configured, jagt refuses to start and prints [the whole list at
once](rules/components.md#what-is-missing-is-said-at-startup-not-at-the-click-that-needed-it).

## Reads cost a model call

Every ticket, merge-request and review-round read is one headless model call
([above](#mcp-access-comes-first)). Budget for it before turning auto-review on: 24 h of polling one request
costs $3–$7.

## Notes

**IntelliJ run configs.** A fresh worktree opens without the base project's run configs. Mark a config *Store
as project file* (Run → Edit Configurations) so it lands under `.run/` — jagt copies those into every worktree.

**MCP pre-approval.** Every agent worktree gets a generated `.claude/settings.local.json` pre-approving jagt's
MCP tools and the agent's own git, so nothing stalls on a prompt nobody is watching.

**UTF-8 locale (kitty, macOS).** kitty follows the libc locale and macOS has no `C.UTF-8`. Without a real
UTF-8 locale, kitty drops non-ASCII input (Cyrillic paste, dictation):

```sh
echo 'export LANG=en_US.UTF-8' >> ~/.zshenv
```
