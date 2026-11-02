## Why

Whether jagt asked the tracker about a task's name was decided by a Jira-shaped pattern in `core`. Every third party is a
configured seam ([0130](../../../docs/decisions/0130-every-third-party-is-a-configured-seam.md)).

## What Changes

- `TrackerWorkflow.isItemKey` says whether a name is an item key: `jira` keeps its shape, `none` answers no.
- Resume, placement, launch, intake and ticket-close ask the tracker only for names the selected workflow calls keys.

## Capabilities

### Modified Capabilities

- `unattended-work`: the tracker jobs and every read of an item act on the workflow's own item keys.
