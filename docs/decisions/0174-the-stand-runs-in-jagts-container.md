# 0174 — The stand runs in jagt's container

[← decisions](README.md) · on [0171](0171-the-image-is-a-base-without-agent-clis.md), beside [0078](0078-a-stand-run-replays-without-a-model.md)

## Status

Accepted 2026-11-16 by the owner's choice of the container as the default in 0.24: "more safe and deliverable".

## Context

- The stand ran the staged jar on the host, so a run needed the host's git, tmux and agent CLI and could reach the human's files.
- `containerTest` already drives jagt's image through compose over HTTP.

## Decision

- `standTest` starts `jagt-stand`, jagt's image plus the agent CLI, with `docker compose`; `-Plocal` keeps the host run.
- `standImage` builds `jagt-stand-base` from the repository `Dockerfile`, then `jagt-stand` from `src/stand/compose/Dockerfile`.
- The run root is bind-mounted at its host path, so worktree paths read from `/state` and every file the checks read are the same on both sides.
- The project is cloned from the run's `origin.git` by url; the board is behind a password login, a random hash in a compose secret.
- The stand's MCP and app servers bind all interfaces and are reached as `host.docker.internal`.
- Compose runs through `StandShell`; the project name is the root name, and the marker names it so `StandCleanup` takes a dead run down.
- The agent credential is passed from the host environment, never written to an image or a tracked file.
- Logs, screens and the recording are copied out before `down -v --remove-orphans`; a failed run's evidence is on the host.
- Without docker a run is skipped with one sentence; `standCleanup` removes the `jagt-stand*` image tags.
- The shipped `Dockerfile` caches `/root/.gradle` in its build stage.
- The image runs `tini` as PID 1 and jagt under the `entrypoint.sh` relaunch loop: a restart kills the JVM alone, tmux survives.

## Measured 2026-11-16

| run | seconds |
|---|---|
| `standImage`, cold / warm | 98 / 13 |
| `standTest -Pmodelfree --rerun-tasks`, container | 91 |
| `standTest -Pmodelfree -Plocal --rerun-tasks` | 162 |

## Known gaps

- Native-Linux file ownership of what the container writes into the root is untested.
- `standImage` rebuilds from layer cache per run (about 13 s), since `standCleanup` removes the tags.

## Rejected

- Testcontainers' `ComposeContainer` for the stand: the run needs the root, ports and marker known before start.
- A named volume for the root: the host reads its files while the run lives.

## Reopen when

- A run in the container fails where the host passes, for a cause the container adds.
