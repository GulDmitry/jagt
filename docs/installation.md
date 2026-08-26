# Installation

[← README](../README.md)

## MCP access comes first

jagt talks to no external service. It reads a ticket, and a review round, by spawning a **headless** one-shot of
your agent CLI and letting that use **your** MCP servers. Two kinds of server cannot answer such a call:

- one that only an **interactive login** authenticates — a headless session authenticates none;
- one that is **plugin-scoped** — a headless session does not load those at all.

`claude mcp list` reports a plugin server as connected the whole time, so nothing looks wrong: the read simply
has no tool for the host it was asked about, and fails naming whichever unauthenticated server it could still
see. Check what a headless call actually has:

```sh
cd "$TMPDIR" && claude "Name your MCP tools for <your tracker> and <your code host>, or say NONE." -p
```

`NONE` means jagt cannot read a ticket. Point it at a servers file instead — a plugin's own `.mcp.json` will do,
and its `${VAR}` placeholders resolve from the environment the backend was started in:

```yaml
# jagt.yml
orchestrator:
  assistant:
    mcpConfig: /path/to/mcp-servers.json
```

That also cuts what each read loads to the servers it needs, which is most of what one costs.

## macOS

| tool | install | needed for |
|------|---------|------------|
| Java 25+ | `sdk install java 25-tem` | the backend |
| an agent CLI | [Claude Code](https://claude.com/claude-code), or the Codex CLI | the agents |
| tmux | `brew install tmux` | persistent agent sessions |
| git | Xcode CLT or `brew install git` | worktrees |
| kitty | `brew install kitty` | the agents terminal |
| IntelliJ IDEA | JetBrains Toolbox | the `ide` checkpoint |
| terminal-notifier | `brew install terminal-notifier` | clickable desktop notifications |
| Node 18+ | `brew install node` | only for `orchestrator.agent.cli=codex` |
| ttyd | `brew install ttyd` | only for `orchestrator.web-terminal.enabled=true` |

## Linux

| tool | install | needed for |
|------|---------|------------|
| Java 25+ | `sdk install java 25-tem` | the backend |
| an agent CLI | [Claude Code](https://claude.com/claude-code), or the Codex CLI | the agents |
| tmux | `apt install tmux` | persistent agent sessions |
| git | `apt install git` | worktrees |
| kitty | `apt install kitty` | the agents terminal |
| libnotify | `apt install libnotify-bin` | desktop notifications |
| a notification daemon | your desktop's own, or `apt install dunst` | showing them — GNOME and KDE bring one, a bare window manager does not |
| an editor CLI | `idea` or `code` on PATH | the `ide` checkpoint |
| lsof | `apt install lsof` | reaping a worktree's leftover processes on `done`; skipped when absent |
| Node 18+ | `apt install nodejs` | only for `orchestrator.agent.cli=codex` |
| ttyd | `apt install ttyd` | only for `orchestrator.web-terminal.enabled=true` |

Then in `jagt.yml`:

```yaml
orchestrator:
  platform: linux         # selects the notifier and the kitty driver
  editorCommand: [idea]   # or [code]
```

`platform` is not optional and not detected: it defaults to `macos`, and jagt refuses to start when it is not
the platform the machine reports. `terminal: warp` is macOS-only for the same reason — it is opened through a
URI scheme.

Everything else is shared with macOS — kitty speaks the same remote-control protocol on both — with one
difference in what a banner can do: **it does not open the board.** `notify-send` carries a click only by
staying alive waiting for the daemon to answer, which a notification jagt fires and forgets cannot. The task is
named in the title instead.

## Run it

```bash
cp jagt.yml.dist jagt.yml
cd orchestrator-backend
./gradlew build stageJar
java -jar build/libs/jagt-run.jar
```

Add at least one project to `jagt.yml` — see [Configuration](configuration.md).

If something is missing or half-configured, jagt refuses to start and prints the **whole** list of problems,
each line naming the key that fixes it. Fix them all, start again.

## Reads cost a model call

Every ticket and review-round read is one headless model call (see [above](#mcp-access-comes-first)). Budget
for it before turning auto-review on: 24 h of polling one request costs $3–$7.

## Notes

**IntelliJ run configs.** A fresh worktree opens without the base project's run configs. Mark a config
*Store as project file* (Run → Edit Configurations) so it lands under `.run/` — jagt copies those into every
worktree, so `ide` opens ready to run.

**MCP pre-approval.** Every agent worktree gets a generated `.claude/settings.local.json` pre-approving jagt's
MCP tools and the agent's own git, so nothing stalls on a permission prompt nobody is watching.

**UTF-8 locale (kitty, macOS).** kitty follows the libc locale and macOS has no `C.UTF-8`. Without a real
UTF-8 locale, kitty drops non-ASCII input (Cyrillic paste, dictation):

```sh
echo 'export LANG=en_US.UTF-8' >> ~/.zshenv
```
