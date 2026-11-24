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
| `./gradlew compileAll` | nothing | every source set of every module compiles; run it after a main symbol is removed or renamed |
| `./gradlew checkScope` | nothing | every source set with sources has Checkstyle gated by `test`, Error Prone, NullAway at error and google-java-format; part of `check` |
| `./gradlew e2eTest` | git + tmux | the task flow over real worktrees, one row per case |
| `./gradlew boardTest` | a Chromium (downloaded on first run) | the board in a real browser |
| `./gradlew linuxDriverTest` | Linux + notify-send/kitty + a display | the Linux drivers against real binaries |
| `./gradlew guardsTest` | shellcheck, bats, jq | the contributor guards in `scripts/guards` |
| `./gradlew promptEval` | the assistant's CLI, and tokens | whether an operator's own words still map onto one command; `-PlocalEndpoint=<url> -PlocalModel=<name>` reads them through `assistant.local` instead, no cloud fallback |
| `./gradlew roleEval -Prole=master` | the assistant's CLI, tokens, minutes, and `.master-eval/root` trusted once | whether the shipped Master brief still reaches a human's verdict |

Timed 2026-11-01, `--rerun-tasks`, 10 CPUs: `test` 80 s, `e2eTest` 309 s, `boardTest` 82 s, `standTest -Pmodelfree` 42 s,
`roleEval -Prole=acceptance` 4–7 min, `-Prole=master` about 5 min; the next refresh compares against these.

Only `test` is in `check`, with its jqwik properties in `:core:propertyTest` after it; every executed suite appends
its seconds, load and power state to `suite-stats.jsonl`;
the build's end names an overrun of the ceiling, and a fresh run (`--rerun-tasks`) slower than its fresh trend.
The rest are asked for by name, each needing something a hermetic run must not depend on.
`roleEval -Prole=master` starts a real session in `.master-eval/root`, and an agent CLI will not work in a
directory nobody has trusted: run `claude` there once and accept. The suite checks that and refuses with the
path rather than sitting at a prompt nobody sees.

`orchestrator-backend/scripts/gradle-slot.sh <command>` caps concurrent Gradle runs on one machine
(`GRADLE_SLOTS`, default 3).

## Testing Linux from a Mac

`orchestrator-backend/scripts/ci-local.sh [job…]` runs a pull request's CI jobs, each the same `gate<Job>` task its CI
job runs: `test`, `heavy` (with `linuxDriverTest` against the real binaries: `notify-send` over a session D-Bus, kitty on
an Xvfb display), `stand` and `install` on a real Linux in a container with a Docker engine of its own, and
`install-macos` natively. It needs Docker and nothing else, and leaves an image, a Gradle cache volume and a Docker
engine volume behind.

## CI

`.github/workflows/ci.yml` and `.gitlab-ci.yml` call the **same scripts** — no CI-only code path, so a green
pipeline and a green laptop mean the same thing.

| job | what it runs |
|-----|--------------|
| `unit` | the hermetic suite |
| `linux` | `e2eTest` + `linuxDriverTest` on a real desktop session |
| `board` | `boardTest` |
| `jar` | the runnable jar, uploaded as a pipeline artifact |

One thing is GitHub-only: attaching the jar to a `v*` tag's release. Neither pipeline needs Docker or a privileged runner.

**Not covered, and not pretended to be:** a code viewer, the macOS AppleScript window raise, the real `claude` CLI,
and the MCP servers its reads need.

## Releasing

1. Bump `version` in `orchestrator-backend/build.gradle` on a branch; open one pull request.
2. Merge it once its required CI is green: `main` is protected.
3. CI on the push to `main` reads the version and, only when tag `vX.Y.Z` does not exist yet, creates it and publishes.

The `release` job builds `jagt-X.Y.Z.jar` and attaches it to that tag's GitHub release only once every suite is green.
Nothing else is published — jagt is on no package manager.

## The board's icon

Two sources: `icon-512.png`, the app icon (transparent rounded corners), and `docs/brand/glyph.png`, the tab glyph:
the flat arrow knot on its own dark tile, rounded corners transparent. From `board/`, regenerate the committed sizes after changing either:

```sh
magick icon-512.png -resize 192x192 icon-192.png
magick icon-512.png -background 'srgb(20,30,72)' -flatten -resize 180x180 apple-touch-icon.png
for s in 16 32 48 64; do magick ../docs/brand/glyph.png -resize ${s}x$s /tmp/g$s.png; done
magick /tmp/g16.png /tmp/g32.png /tmp/g48.png favicon.ico
printf '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">\n<image width="32" height="32" href="data:image/png;base64,%s"/>\n</svg>\n' \
  "$(base64 -i /tmp/g64.png | tr -d '\n')" > favicon.svg && cp favicon.svg ../site/favicon.svg
```

The apple icon is flattened on the app icon's own dark blue because iOS masks its own corners; `favicon.svg` keeps a
32-unit viewBox, since the board draws its waiting dot into it.

## Where things go

[`ARCHITECTURE.md`](../ARCHITECTURE.md) is the map — what kinds of thing jagt has and where a new one belongs.
[`AGENTS.md`](../AGENTS.md) plus `docs/rules/` hold the conventions. [`openspec/specs/`](../openspec/specs/) holds what jagt
does: a rule a requirement, a situation somebody already worked out a scenario.
