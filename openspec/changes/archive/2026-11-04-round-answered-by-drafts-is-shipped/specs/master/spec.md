## MODIFIED Requirements

### Requirement: In act the Master moves for you
With `roles.master.mode: act` the Master SHALL ship a `ready` round, approve a plan that holds as `approve-plan` does, and
answer a question. It SHALL press `add-repo` for a repository a session asked for (`MasterRepoJob`) and deploy a
`REVIEWED` or `APPROVED` task once (`MasterDeployJob`). A permission, and a plan asked for in `plan` mode, stay yours.

#### Scenario: A round answered by drafts alone is shipped
- **WHEN** a ship was pressed on tree X and the next round is answered by new `review_replies.md` drafts, the tree unchanged
- **THEN** the Master ships again; the same tree with the same drafts stays shipped
