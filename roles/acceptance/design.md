# acceptance — design

## Why it exists

A deployed change is checked against its ticket by someone other than the session that built it. Acceptance does it
once per deploy, and organises its checks so a later check reuses what an earlier one learnt and spends fewer tokens.

## Responsibility

- Owns the check of a deployed change against its ticket, one row per criterion.
- Owns its own workspace of scenarios, steps and flows, and the `index.md` that lists them.
- Does not own the code fix: a row not met goes to the session as an `acceptance-round`.
- Does not own the deploy: the human, or the Master in `act`, presses it.
- Does not own a stop's lift: the human does.
- Owns its interactive session, where the human writes scenarios with it or answers its questions over several rounds.

## Who triggers it

jagt alone: a task DEPLOYED, its deploy pipelines green, the role's `environments` naming one. Never a model's answer.

## Use cases

- **WHEN** ABC-42 deploys and a scenario in the workspace covers each criterion
  **THEN** acceptance runs those flows and answers one row per criterion, writing nothing new
- **WHEN** a criterion no scenario covers
  **THEN** acceptance decomposes it into WHEN/THEN scenarios and reusable steps, writes the flow, runs it headless on a
  listed environment and records it in the workspace's `index.md`
- **WHEN** a criterion needs what only a human knows (a threshold, a login it lacks)
  **THEN** the row is `unprovable` behind a `question`, and the answer goes into the index for the next check
- **WHEN** a row is not met
  **THEN** the session gets an `acceptance-round`, as before
- **WHEN** you run `acceptance`, or `scenarios` on ABC-42 before its deploy
  **THEN** an interactive acceptance session opens in its workspace, ABC-42's ticket and any open question loaded
- **WHEN** `workspace: none`
  **THEN** acceptance reads memoryless and writes nothing, as before workspaces

## Usage

- On by `roles.acceptance.enabled`; everything else in `roles/acceptance/role.yml`.
- The workspace is `roles/acceptance/workspace/` by default, or a repository of the user's; `setup.md` asks the
  technology the project has, the path and the layout.
- Layout: `index.md` read first (environments, roles and how each signs in, scenarios known), `scenarios/`, `steps/`, `flows/`.

## Guards

- Write and Edit under the workspace alone; the one extra command is the flows command with a listed `BASE_URL`.
- A flow may click and create data on a listed environment, never production; no push, no base branch, no credential in
  context: a flow reads its login from the workspace's gitignored `.env`.
- jagt never commits the workspace; every report names the flows it wrote or changed, for the user to review.

## Cost against a session doing the same

- A session checking its own deploy re-derives sign-in, navigation and meaning every time, and judges its own work.
- Acceptance pays once to write a flow for a kind of criterion, then a run and a read per check.
- Measured from `acceptance check` spend per deploy; no fall in it over repeated deploys of one app reopens
  [0158](../../docs/decisions/0158-acceptance-keeps-its-checks-in-a-workspace.md).
