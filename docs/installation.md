# Installation

[← README](../README.md)

On a server, see [docs/server.md](server.md).
The installers are the install path; [building from source](#build-from-source) is the other.

## Technologies

Every one is spawned by **bare name**, resolved on PATH and then in the usual install directories. Give an
absolute path in `jagt.yml` to pin one.

| technology | what jagt does with it | macOS · Linux |
|---|---|---|
| Java 25+ | runs the backend | `sdk install java 25-tem` |
| git | worktrees, branches, and the merge `deploy` makes | Xcode CLT · `apt install git` |
| `curl` | every hook jagt installs and every headless read | in the OS · `apt install curl` |
| [Claude Code](https://claude.com/claude-code) | reads every ticket, merge request and review round, headless — whichever CLI writes the code | its own installer |
| [tmux](https://github.com/tmux/tmux) | the session an agent lives in, outliving the board and the window | `brew install tmux` · `apt install tmux` |
| `pgrep` | reading whether an agent still runs in its window | in the OS · `apt install procps` |
| [kitty](https://sw.kovidgoyal.net/kitty/) | the window `focus` raises, one tab per agent; not needed with `terminal: none` | `brew install kitty` · `apt install kitty` |
| [Ghostty](https://ghostty.org) 1.3+ | the alternative to kitty on macOS, `terminal: ghostty` | `brew install --cask ghostty` |
| [bubblewrap](https://github.com/containers/bubblewrap) + socat | Claude's sandbox, only when `sandbox.enabled: true` keeps a session's writes in its task | built in · `apt install bubblewrap socat` |
| A code viewer, for example [IntelliJ IDEA](https://www.jetbrains.com/idea/) or [VS Code](https://code.visualstudio.com/) | opens the worktree at the `code` checkpoint | its launcher on PATH, or installed as an application |

jagt refuses to start when one of those is missing — bar Claude Code, whose absence shows at the first read.
The rest are optional, and what selects each is in `jagt.desktop.yml.dist`:

| technology | needed for |
|---|---|
| [Codex CLI](https://developers.openai.com/codex/cli) + [Node 18+](https://nodejs.org/) | `agent.cli: codex` — an alternative to Claude Code for the agents, reached over a stdio bridge |
| [OpenCode](https://opencode.ai/) | `agent.cli: opencode` — an alternative for the agents, on a local model through `opencode.endpoint` |
| [terminal-notifier](https://github.com/julienXX/terminal-notifier) | macOS notifications you can click; without it, osascript banners you cannot |
| [libnotify](https://gitlab.gnome.org/GNOME/libnotify) + a daemon such as [dunst](https://dunst-project.org/) | Linux notifications; GNOME and KDE bring the daemon, a bare WM does not |
| [lsof](https://github.com/lsof-org/lsof), `pkill` | reaping a worktree's leftover processes on `done`; said once at start and skipped when absent |
| [Docker](https://www.docker.com/) | `standTest` runs jagt's container; without it the run is skipped, `-Pengine=host` runs the host; `-Pengine=kind` also needs [kind](https://kind.sigs.k8s.io/) and kubectl |
| [OpenSpec](https://github.com/Fission-AI/OpenSpec) | a repository holding `openspec/`: its hand-back is gated and `ship` folds the change; `npm i -g @fission-ai/openspec` |

## Where an installed jagt keeps things

`install.sh` and `install.ps1` install for your user, with no admin rights, and check each download against its published checksum.

| | macOS | Linux | Windows |
|---|---|---|---|
| install root | `~/Library/Application Support/jagt` | `$XDG_DATA_HOME/jagt`, else `~/.local/share/jagt` | `%LOCALAPPDATA%\jagt` |
| launcher | `~/.local/bin/jagt` | `~/.local/bin/jagt` | `jagt.cmd` in `<root>\bin` |

`JAGT_HOME` names another root, `JAGT_BIN` another launcher folder and `JAGT_VERSION` a release instead of the latest.
The installer adds the launcher's folder to your PATH on Windows; on macOS and Linux it says so when the folder is missing from it.

Inside the root:

| path | holds |
|---|---|
| `runtime/` | a Temurin 25 JRE the installer downloads from Adoptium and checks against its checksum; you need no Java of your own |
| `versions/<v>/jagt.jar` | one folder per installed release; `current` points at the running one |
| `jagt.yml` | your configuration, absent until the board adds the first project or you copy `jagt.desktop.yml.dist` to it |
| `roles/`, `jagt.desktop.yml.dist` | jagt's `.dist` files, rewritten at every start; your own copies stay as they are |
| `data/` | everything jagt writes: state, tasks, worktrees, logs |

Everything jagt writes lives in that one data folder, `data/` in the root by default ([the tree](rules/components.md#the-data-folder)).
Name another with `orchestrator.data` in `jagt.yml`, `ORCHESTRATOR_DATA` or `JAGT__orchestrator__data`.
A repository stays where it is.
With no `jagt.yml` and no project it starts on the defaults, and the board adds the first project.
What an older version kept in the jagt folder's root, `~/.jagt` or flat in the data folder moves into the tree at the first start.

## Upgrading

An installed jagt, its jar under `versions/<v>/`, keeps a newer release beside the running one and checks its checksum.
By default an installed jagt asks the release feed, GitHub's releases, once an hour.
`releaseFeed.kind: none` in `jagt.yml` stops that contact.
While a newer release is kept, the board offers `upgrade`.
With `releaseFeed.autoUpgrade: true` and the launcher, jagt switches by itself once no agent session is up, then exits with 75
so the launcher starts the new release.

## Stopping

Stop an installed jagt by sending TERM or INT to the launcher, or press Ctrl+C in its terminal.
The launcher passes the signal to jagt, waits until it has ended and then ends itself, so the port is free.
On Windows, end the launcher's whole process tree: `taskkill /T /PID <pid>`.

## MCP access comes first

jagt talks to no external service: it reads a ticket, and a review round, by spawning a **headless** one-shot of
Claude Code, which uses **your** MCP servers. Two kinds of server cannot answer such a call:

- one that only an **interactive login** authenticates — a headless session authenticates none;
- one that is **plugin-scoped** where `assistant.settingSources` drops `user` — the session then loads none.

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
  platform: linux         # optional override of the detected platform
  editorCommand: [idea]   # or [code]; editorDiffCommand is optional
```

`platform` is detected from the machine; set it only to override, and jagt refuses to start when it is not
what the machine reports. Everything else is shared — kitty speaks the same remote-control protocol on both — bar one
difference: **a Linux notification does not open the board.** `notify-send` carries a click only by staying
alive waiting for the daemon, which a fire-and-forget notification cannot; the task is named in the title
instead.

## Notes

**Run configs of a code viewer** (IntelliJ, for example). A fresh worktree opens without the base project's run configs. Mark a config *Store
as project file* (Run → Edit Configurations) so it lands under `.run/`, which jagt copies into every worktree.

**MCP pre-approval.** Every agent worktree gets a generated `.claude/settings.local.json` pre-approving jagt's
MCP tools and the agent's own git, so nothing stalls on a prompt nobody is watching.

**UTF-8 locale (kitty, macOS).** kitty follows the libc locale and macOS has no `C.UTF-8`. Without a real
UTF-8 locale, kitty drops non-ASCII input:

```sh
echo 'export LANG=en_US.UTF-8' >> ~/.zshenv
```

## Build from source

Needs Java 25+ and the tools above.

```sh
git clone https://github.com/GulDmitry/jagt.git && cd jagt
cp jagt.desktop.yml.dist jagt.yml
cd orchestrator-backend
./gradlew build stageJar
java -jar build/libs/jagt-run.jar
```

`jagt.yml` sits at the repository root; [Configuration](configuration.md) explains it.
The board is at http://localhost:8290.

> [!IMPORTANT]
> Run the **staged** `jagt-run.jar`. `./gradlew build` rewrites `jagt.jar` in place, and a JVM still reading it
> dies with a `NoClassDefFoundError` that hides the real error — [Troubleshooting](troubleshooting.md).
