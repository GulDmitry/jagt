## Why

The New task form names projects by key alone, so the human cannot tell which repository is which, and a task
started with no project and no plan left the choice to a ticket read the human may not want.

## What Changes

- Hovering a project in the New task form shows its whole `jagt.yml` entry and its repository's address.
- Start with no project ticked and `plan first` unticked says so in the form and sends nothing.

## Capabilities

### Modified Capabilities

- `board`: the launch row's project hover, and the project it needs.
