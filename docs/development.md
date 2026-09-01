# Development

[← README](../README.md)

## Working on jagt with any agent CLI

The repository briefs whichever CLI you open it with and points all of them at a running backend's MCP server.
Those sessions carry no worktree header, so they count as Master. Which file each CLI reads, and how it reaches
the MCP server:
[`docs/rules/components.md`](rules/components.md#whoever-works-on-jagt-reads-the-same-file-and-reaches-the-same-server).

**Rules live in [`AGENTS.md`](../AGENTS.md)** — the hard ones inline, the rest in `docs/rules/`. Never write a
project rule into a vendor-named file.

## Test suites

| task | needs | answers |
|------|-------|---------|
| `./gradlew test` | nothing | the fast hermetic gate; runs everywhere |
| `./gradlew e2eTest` | git + tmux | the task flow over real worktrees, one row per case |
| `./gradlew boardTest` | a Chromium (downloaded on first run) | the board in a real browser |
| `./gradlew linuxDriverTest` | Linux + notify-send/kitty + a display | the Linux drivers against real binaries |
| `./gradlew promptEval` | the assistant's CLI, and tokens | whether an operator's own words still map onto one command |

Only `test` is in `check`; the rest are asked for by name, each needing something a hermetic run must not
depend on.

## Testing Linux from a Mac

`orchestrator-backend/scripts/linux-suite.sh` runs four tasks on a real Linux in a container: the unit suite on
a Linux JVM, `e2eTest` with real git and tmux, `boardTest` in a headless Chromium, and `linuxDriverTest` against
the real binaries (`notify-send` over a session D-Bus, kitty on an Xvfb display answering remote control). It
needs Docker and nothing else, and leaves an image and a Gradle cache volume behind.

## CI

`.github/workflows/ci.yml` and `.gitlab-ci.yml` call the **same scripts** — no CI-only code path, so a green
pipeline and a green laptop mean the same thing.

| job | what it runs |
|-----|--------------|
| `unit` | the hermetic suite |
| `linux` | `e2eTest` + `linuxDriverTest` on a real desktop session |
| `board` | `boardTest` |
| `jar` | the runnable jar, uploaded as a pipeline artifact |

Two things are GitHub-only: the unit suite and jar build on macOS, the platform jagt is developed on, and
attaching the jar to a `v*` tag's release. Neither pipeline needs Docker or a privileged runner.

**Not covered, and not pretended to be:** IntelliJ, the macOS AppleScript window raise, the real `claude` CLI,
and the MCP servers its reads need.

## Releasing

1. Bump `version` in `orchestrator-backend/build.gradle`.
2. Commit that bump — the tag must point at it, or the `jar` job reads the old version and attaches the
   previous jar to the release with the pipeline green.
3. Tag the bump commit: `git tag vX.Y.Z`.
4. Push the tag: `git push origin vX.Y.Z`.

The `jar` job builds `jagt-X.Y.Z.jar` and creates or updates the GitHub release for that tag with it attached.
Nothing else is published — jagt is on no package manager.

## Where things go

[`ARCHITECTURE.md`](../ARCHITECTURE.md) is the map — what kinds of thing jagt has and where a new one belongs.
[`AGENTS.md`](../AGENTS.md) plus `docs/rules/` hold the rules. [`USE-CASES.md`](../USE-CASES.md) holds the
one-line answer to a situation somebody already worked out.
