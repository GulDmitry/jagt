## Why

A Compose stack a session started inside its worktree keeps running after the task ends and the worktree is removed.

## What Changes

- Removing a worktree first runs `docker compose down -v --remove-orphans` for every Compose project whose compose file lies in it.
- The driver is chosen by `orchestrator.containers` (`docker-compose` default, `none`); a host that cannot answer never stops the removal.

## Impact

- Spec: `runtime`.
- `port/WorktreeContainers`, `DockerComposeContainers`, `NoWorktreeContainers`, `GitCommands`, `GitWorktrees.removeWorktree`, `jagt.yml.dist`.
