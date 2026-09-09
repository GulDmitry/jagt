# Installation

[← README](../README.md)

## Technologies

Every one is spawned by **bare name**, resolved on PATH and then in the usual install directories. Give an
absolute path in `jagt.yml` to pin one.

| technology | what jagt does with it | macOS · Linux |
|---|---|---|
| Java 25+ | runs the backend | `sdk install java 25-tem` |
| git | worktrees, branches, and the merge `deploy` makes | Xcode CLT · `apt install git` |
| [Claude Code](https://claude.com/claude-code) | reads every ticket, merge request and review round, headless — whichever CLI writes the code | its own installer |
| [tmux](https://github.com/tmux/tmux) | the session an agent lives in, outliving the board and the window | `brew install tmux` · `apt install tmux` |
| [kitty](https://sw.kovidgoyal.net/kitty/) | the window `focus` raises, one tab per agent | `brew install kitty` · `apt install kitty` |
| [IntelliJ IDEA](https://www.jetbrains.com/idea/) or [VS Code](https://code.visualstudio.com/) | opens the worktree at the `ide` checkpoint | `idea` or `code` on PATH |

jagt refuses to start when one of those is missing — bar Claude Code, whose absence shows at the first read.
The rest are optional, and what selects each is in `jagt.yml.dist`:

| technology | needed for |
|---|---|
| [Codex CLI](https://developers.openai.com/codex/cli) + [Node 18+](https://nodejs.org/) | `agent.cli: codex` — an alternative to Claude Code for the agents, reached over a stdio bridge |
| [terminal-notifier](https://github.com/julienXX/terminal-notifier) | macOS notifications you can click; without it, osascript banners you cannot |
| [libnotify](https://gitlab.gnome.org/GNOME/libnotify) + a daemon such as [dunst](https://dunst-project.org/) | Linux notifications; GNOME and KDE bring the daemon, a bare WM does not |
| [lsof](https://github.com/lsof-org/lsof) | reaping a worktree's leftover processes on `done`; skipped when absent |

## MCP access comes first

jagt talks to no external service: it reads a ticket, and a review round, by spawning a **headless** one-shot of
Claude Code, which uses **your** MCP servers. Two kinds of server cannot answer such a call:

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

Every ticket, merge-request and review-round read is one such call. Budget for it before turning auto-review
on: 24 h of polling one request costs $3–$7.

## Linux

```yaml
orchestrator:
  platform: linux         # selects the notifier and the kitty driver
  editorCommand: [idea]   # or [code]
```

`platform` is not detected: it defaults to `macos`, and jagt refuses to start when it is not what the machine
reports. Everything else is shared — kitty speaks the same remote-control protocol on both — bar one
difference: **a Linux notification does not open the board.** `notify-send` carries a click only by staying
alive waiting for the daemon, which a fire-and-forget notification cannot; the task is named in the title
instead.

## Notes

**IntelliJ run configs.** A fresh worktree opens without the base project's run configs. Mark a config *Store
as project file* (Run → Edit Configurations) so it lands under `.run/`, which jagt copies into every worktree.

**MCP pre-approval.** Every agent worktree gets a generated `.claude/settings.local.json` pre-approving jagt's
MCP tools and the agent's own git, so nothing stalls on a prompt nobody is watching.

**UTF-8 locale (kitty, macOS).** kitty follows the libc locale and macOS has no `C.UTF-8`. Without a real
UTF-8 locale, kitty drops non-ASCII input:

```sh
echo 'export LANG=en_US.UTF-8' >> ~/.zshenv
```
