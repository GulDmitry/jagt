## Why

With `master.mine` keeping a plan or a round, the Master read it, wrote `ready` and pressed nothing: the card became
yours with no ping, because the ping fired on a report and no report came. A step can become yours without one.

## What Changes

- One job pings you when a task's owner turns YOU, or its advice changes while it stays yours (`YourMoveJob`).
- `AgentStatusReports` pings nothing; a restart pings nothing for a card already yours.

## Capabilities

### New Capabilities

### Modified Capabilities

- `attention`: the ping follows the projection's owner, not a report.
