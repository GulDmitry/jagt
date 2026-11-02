## Why

`ship` always opened a request and the flow always waited on one, so a repository with no code host could not be shipped,
and a request URL was matched to a project by a substring of its path. The agent key chose the code host's replay double.
Every third party is a configured seam ([0130](../../../docs/decisions/0130-every-third-party-is-a-configured-seam.md)).

## What Changes

- With `codeHost: none` a ship asks for the push alone; the task rests at REVIEW_PENDING with the deploy next, and no sweep waits.
- A request URL belongs to the project whose remote names its host and exact path segments; a project with no remote is skipped.
- `replay` is a `codeHost` value; `agent.cli` no longer selects a code host driver.
- The board's request chip reads `request`, host-neutral.

## Capabilities

### Modified Capabilities

- `git`: ship without a code host; a request belongs to its repository by host and path.
- `board`: the request chip's label.
- `deploy-check`: `replay` is a code host.
