## MODIFIED Requirements

### Requirement: Revert refuses rather than guess
`revert` SHALL take out the last deploy's merge, DEPLOY_CONFLICT included; it refuses with a by-hand recipe on no
`deployCommit`, commit absent, already reverted, or conflict. It walks back the repositories holding a merge commit,
each **forgetting** it; REVERTED once all that landed is out, both half-states **stamped on the task**.

#### Scenario: Revert
- **WHEN** `revert ABC-42`
- **THEN** reverse order, only what landed

#### Scenario: Deployed twice
- **WHEN** `revert <task>` after several deploys
- **THEN** only the last; earlier ones by hand: `git log --merges --grep ABC-42`, `git revert -m 1 <sha>`

### Requirement: Deploy stops at the first conflict
`deploy` SHALL check every repository deployable before the **first** push, land them in the task's order, and stop at
the first conflict: DEPLOY_CONFLICT, naming both sides from there.

#### Scenario: Conflict
- **WHEN** a conflict: resolve it there (`git add`), then `deploy`
- **THEN** DEPLOY_CONFLICT until you do

#### Scenario: Master acts
- **WHEN** a conflict where the Master acts
- **THEN** the session resolves it there; once staged, jagt finishes the deploy

#### Scenario: Multi-repo deploy
- **WHEN** `deploy ABC-42` and one conflicts after another landed
- **THEN** the next `deploy` resumes there, or `revert` takes out what landed

#### Scenario: Break-off
- **WHEN** it breaks off for something no worktree fixes
- **THEN** status untouched, naming what is already live
