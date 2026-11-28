# 0272 — Compose names its stack after the worktree it runs in

[← decisions](README.md) · spec: [`platform`](../../openspec/specs/platform/spec.md) · seam: [`seams`](../rules/seams.md)

## Status

Accepted 2026-11-28.

## Context

- A session carried one `COMPOSE_PROJECT_NAME`, from its first worktree; a command run after `cd` into a second repository's
  worktree started that repository's compose files under the first repository's name: a duplicate stack whose services clashed.
- A process environment is fixed at the session's start; the worktree a command runs in is known only when it runs.
- Compose reads a shell variable before any env file, so a per-worktree env file cannot override the session's variable,
  and a `.env` in the worktree is the repository's own file.

## Decision

- **jagt writes `<worktree>/.jagt/compose-project`** holding `DockerComposeContainers.projectName` of that worktree,
  whenever it fills a worktree, the one a repository attached later included (`WorktreeContainers.writeStackName`).
- **The rule lives in Java alone**: `stop` and the file use the same method; the shim holds no naming logic.
- **A shim `docker` and `docker-compose` in the session's first worktree, in `.jagt/bin`**, first on its `PATH`
  (`WorktreeContainers.installCommands`): they walk up from the current directory to the nearest name file, export its content as
  `COMPOSE_PROJECT_NAME`, then run the next such command on `PATH`.
- **The session's own variable stays** as the name where no folder up the path holds a name file.
- A shim is generated text beside the git hooks jagt already writes, not a tracked script: `scripts-are-java` does not reach it.

## Rejected

- A per-worktree compose env file: the session's variable wins over it.
- One variable per session: fixed at the first worktree, the defect itself.
- Naming logic in the shim: a second copy of the rule, drifting from `stop`'s.
- Rewriting the agent's command line: a model's command is not parsed by jagt.

## Reopen when

- A compose command is run by an absolute path to the binary and stacks clash again.
- Compose offers a project name per working directory.
