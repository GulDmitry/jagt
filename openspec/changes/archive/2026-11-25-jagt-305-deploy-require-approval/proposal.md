## Why
A request must be approved on the code host before anyone deploys it; `deploy` is offered at `REVIEWED` today.

## What Changes
- `deploy.requireApproval` (default `false`, `true` in the server starter) removes `deploy` from `REVIEWED` for every actor.
