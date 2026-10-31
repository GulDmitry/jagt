## Why

The deploy branch was guessed from `origin/dev` alone, and only Gradle and npm repositories got a verify command.
The page `board-workspace` has no room left for the rest of project setup, so it moves to a spec of its own.

## What Changes

- The reading offers the remote's branches as last fetched, prefilling `dev`, `develop` or `staging`, never the base.
- `mvnw`, and a `Makefile` holding a `test:` target, suggest a verify command.
- The requirement moves from `board-workspace` to `project-setup`.

## Capabilities

### New Capabilities

- `project-setup`: adding a project from the board, now with the remote's branches and more verify commands.

### Modified Capabilities

- `board-workspace`: the requirement is removed, it lives in `project-setup`.
