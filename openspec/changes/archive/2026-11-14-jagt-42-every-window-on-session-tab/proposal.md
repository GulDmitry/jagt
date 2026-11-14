## Why

A login window, an MCP login and the acceptance scenarios session are not tasks, so neither the Session tab nor `say`
reach them: the human leaves the board for a terminal. A code-host login also opens a browser the board cannot show.

## What Changes

- Every window jagt opens, a task's or not, is on the Session tab and takes `say`.
- A GitHub login shows its one-time code and device URL whole on the board; jagt opens no browser.
- A GitLab login uses the device flow where the CLI offers it (`glab auth login --device`, GitLab 17.9 or later);
  else its own login prompt shows on the Session tab and `say` answers it.

## Impact

- `board`: new requirement for the Session tab's windows; the lost-reach requirement states the login's form.
- Out of scope: a browser jagt drives. `board-workspace` and `runtime` stay at their budgets, untouched.
