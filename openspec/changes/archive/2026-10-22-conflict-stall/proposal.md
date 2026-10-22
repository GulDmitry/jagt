## Why

In `act`, a deploy conflict was relayed once into a session whose CLI refused every edit in the deploy worktree, which
its brief never listed as its own; the task then sat in DEPLOY_CONFLICT for 40 minutes, nobody told. A second deploy
press brought the conflict back and the session was not told again: the ask was remembered in memory, by its text.

## What Changes

- The deploy worktree is the session's: listed in its brief, admitted by its CLI, at spawn and at the hand-off.
- Each deploy press is a new conflict, handed again; the hand-off is kept in `state.json`.
- A handed conflict whose worktree does not change for `conflictStallMinutes` tells the human once and says why on the card.

## Capabilities

### New Capabilities

### Modified Capabilities

- `task-flow`: a deploy conflict in act is handed as the session's own worktree, and a stalled one is the human's.
