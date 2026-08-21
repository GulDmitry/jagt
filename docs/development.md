# Development

[← README](../README.md)

## Working on jagt with any agent CLI

The repository briefs whichever CLI you open it with, and points all of them at a running backend's MCP server.
Those sessions carry no worktree header, so they count as Master.

| CLI | reads the rules from | reaches jagt's MCP through |
|-----|----------------------|----------------------------|
| Claude Code | `AGENTS.md` via the `CLAUDE.md` symlink | `.mcp.json` (HTTP) |
| Codex | `AGENTS.md` (its own convention) | `.codex/config.toml` — the stdio bridge, so it needs Node |
| Qwen Code | `AGENTS.md` via `.qwen/settings.json` | `.qwen/settings.json` (HTTP) |

**There is one rules file: [`AGENTS.md`](../AGENTS.md).** Never write a project rule into a vendor-named file.

> [!NOTE]
> Codex loads a project layer only for a *trusted* project, and resolves the bridge from the working
> directory — start it at the repository root.

## Test suites

| task | needs | answers |
|------|-------|---------|
| `./gradlew test` | nothing | the fast hermetic gate; runs everywhere |
| `./gradlew e2eTest` | git + tmux | the task flow over real worktrees, one row per case |
| `./gradlew boardTest` | a Chromium (downloaded on first run) | the board in a real browser |
| `./gradlew linuxDriverTest` | Linux + notify-send/kitty + a display | the Linux drivers against real binaries |
| `scripts/dashboard-layout-smoke.sh` | tmux + a built jar | the console's layout through a real PTY |
| `scripts/tui-push-repaint-smoke.sh` | tmux + a built jar | the console repainting on a pushed event |

Only `test` is in `check`. The rest are asked for by name, because each needs something a hermetic run must
not depend on.

## Testing Linux from a Mac

`orchestrator-backend/scripts/linux-suite.sh` runs the suites on a real Linux without a second machine — the
container is one. Four tasks in order: the unit suite on a Linux JVM, `e2eTest` with real git and tmux,
`boardTest` in a headless Chromium, and `linuxDriverTest` against the real binaries (`notify-send` over a
session D-Bus, kitty on an Xvfb display answering remote control).

Needs Docker and nothing else. It leaves an image and a Gradle cache volume behind.

It earns its keep: the first run found `tmux-command` shipping as `/opt/homebrew/bin/tmux`, so every task on
Linux died with "Failed to start command" before its agent started.

## CI

`.github/workflows/ci.yml` and `.gitlab-ci.yml` call the **same scripts**. There is no CI-only code path, so a
green pipeline and a green laptop mean the same thing.

| job | what it runs |
|-----|--------------|
| `unit` | the hermetic suite |
| `linux` | `e2eTest` + `linuxDriverTest` on a real desktop session |
| `board` | `boardTest` |
| `smoke` | the two real-PTY tmux scripts against the built jar |

GitHub additionally runs the unit suite and the layout smoke on macOS, the platform jagt is developed on.
Neither pipeline needs Docker or a privileged runner — the container image is for developers on a Mac, and
installs from the same deps script.

**Not covered, and not pretended to be:** IntelliJ, the macOS AppleScript window raise, the Warp URI scheme,
the real `claude` CLI, and a live code host or tracker.

## Where things go

[`ARCHITECTURE.md`](../ARCHITECTURE.md) is the map — what kinds of thing jagt has and where a new one belongs.
[`AGENTS.md`](../AGENTS.md) holds the rules. [`USE-CASES.md`](../USE-CASES.md) holds the one-line answer to a
situation somebody already worked out.
