## MODIFIED Requirements

### Requirement: No verdict gates deploy
`FlowRules`' `DEPLOY` SHALL ask only for an open request, plus DEPLOY_CONFLICT; NEW, PLAN_PENDING, IN_PROGRESS,
VERIFYING, SHIPPING, REVERTED and DONE are refused; `ship` still runs from DEPLOYED. The confirm's `project → branch`
line comes from `RepoView.deployBranch`.

#### Scenario: Confirms
- **WHEN** you press Deploy
- **THEN** one `project → branch` line per repository, nothing else
- **AND** Revert: the branches it pushes to, the last deploy only

#### Scenario: REVERTED
- **WHEN** a task is REVERTED: `focus`, then `ship` or `done`
- **THEN** no `deploy`: re-merging it brings nothing; the agent's reports move nothing

### Requirement: Ship hands the work to the agent
`ship` SHALL send ONE `ShipService` instruction for every repository holding work (changed in its worktree or ahead of
its target): commit, push, open or update a request against its own target, every link in one `update_agent_status`
(`reviewRequests`). jagt runs no git, calls no host; the task waits in SHIPPING. Nothing to ship
or deploy is no failure (`NothingToDeployException`, `holdsWork`): passed over and named, no request opened.

#### Scenario: Ship
- **WHEN** `ship ABC-42`
- **THEN** a commit, push and request per repository onto its own base

### Requirement: Push guard lives in the worktree
`ToolGate` (`POST /api/agent/tool`, the command LINE) and `.jagt/hooks/pre-push` (`WorktreeHooks`, the refs) SHALL
refuse only a push not to the task's branch. `core.hooksPath` goes via `GIT_CONFIG_*` on the launch command
(`WorktreeHooks.gitEnv`): that session and children, no repository config.
`--no-verify` skips it; `deploy`, `revert` and a human's shell run ungated.

#### Scenario: Hooks
- **WHEN** a client-side hook fires
- **THEN** a stub runs the repository's own, re-resolved at run time with the override off, guard first
