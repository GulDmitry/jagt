## Why

On a stand run the agent printed `Error: Invalid MCP configuration: …` and exited at launch; the task stayed `starting`
until the watchdog said "no sign of life" ten minutes later, with no cause on the card or in the log.

## What Changes

- An agent gone at `NEW` is told within seconds as an ended session, with the last lines its window showed.
- The 15s tail holds no process, so the window reads as exited while it shows them.

## Capabilities

### New Capabilities

### Modified Capabilities

- `attention`: an exit at `NEW` is stamped with its last lines.
- `runtime`: an exited agent's tail holds no process.
