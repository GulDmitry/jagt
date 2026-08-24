# Installation

[← README](../README.md)

**Before anything else:** your agent CLI (Claude Code by default) must already have MCP access to the systems
your agents will use — your issue tracker and your code host. jagt itself talks to no external service.

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
| Node 18+ | `brew install node` | only for `orchestrator.agent=codex` |
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
| an editor CLI | `idea` or `code` on PATH | the `ide` checkpoint |
| Node 18+ | `apt install nodejs` | only for `orchestrator.agent=codex` |
| ttyd | `apt install ttyd` | only for `orchestrator.web-terminal.enabled=true` |

Then in `application.yml`:

```yaml
orchestrator:
  platform: linux        # selects the notifier and the kitty driver
  editor-command: [idea] # or [code]
```

Everything else is shared with macOS — kitty speaks the same remote-control protocol on both.

## Run it

```bash
cp config.json.dist config.json
cd orchestrator-backend
./gradlew build stageJar
java -jar build/libs/jagt-run.jar
```

Add at least one project to `config.json` — see [Configuration](configuration.md).

If something is missing or half-configured, jagt refuses to start and prints the **whole** list of problems,
each line naming the key that fixes it. Fix them all, start again.

## Reads cost a model call

Every ticket and review-round read is a headless model call that uses **your** MCP servers — jagt holds no
credential and talks to no service itself. A tracker or code-host server that only an interactive login can
reach cannot answer one: declare a token-based server for it with
`orchestrator.assistant.mcp-config`.

Budget for it before turning auto-review on: 24 h of polling one request costs $3–$7.

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
