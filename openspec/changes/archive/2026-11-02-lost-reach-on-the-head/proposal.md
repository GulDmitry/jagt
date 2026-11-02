## Why

A code host CLI signed out stopped two deploys at once on a question nobody could answer by lifting a stop
([0130](../../../docs/decisions/0130-every-third-party-is-a-configured-seam.md)). A failure of jagt's own reach is jagt's:
one mark on the board's head, ended by the next read that succeeds.

## What Changes

- A driver that cannot reach its host (CLI missing, signed out, 401/403, host unreachable) answers with a lost reach, typed, naming driver, host and its login.
- jagt holds a lost reach per driver and host, in memory, until a read through that pair succeeds; reads go on every tick.
- A deploy read that never reached the host stays open as `waiting on its pipeline`, counts toward no `DEPLOY_UNREAD` and stops no task; a read that reached the host and failed counts as before.
- The board's head shows one mark naming driver and host with the fix in its hover; the `login` verb opens the driver's login in a window of its own.
- `loginCommand` names a `command` driver's login; a `pipelineCommand` exiting 77 says it is signed out.
- Every armed pipeline retry names the repository it belongs to, so no read guesses a project from an address.

## Capabilities

### Modified Capabilities

- `board`: the head carries jagt's lost reach and its login.
- `attention`: a lost reach is nobody's move.
- `deploy-check`: a lost reach leaves the deploy open and stops nothing.
