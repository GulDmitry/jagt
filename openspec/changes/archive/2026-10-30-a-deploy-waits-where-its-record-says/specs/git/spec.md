## MODIFIED Requirements

### Requirement: Deploy stops at the first conflict
`deploy` SHALL check every repository deployable before the **first** push, land them in the task's order, and stop at
the first conflict: DEPLOY_CONFLICT, naming both sides from there.

#### Scenario: Conflict
- **WHEN** a conflict: resolve it (`git add`), then `deploy`
- **THEN** DEPLOY_CONFLICT until you do

#### Scenario: Multi-repo deploy
- **WHEN** `deploy ABC-42` and one conflicts after another landed
- **THEN** the next `deploy` resumes there, or is refused naming the deploy worktree if it was deleted by hand

#### Scenario: Break-off
- **WHEN** it breaks off for something no worktree fixes
- **THEN** status untouched, naming what is already live
