## MODIFIED Requirements

### Requirement: The steps the Master leaves you are checked at start
`roles.master.mine` SHALL name only `task/MasterRight` steps, `TaskAction` ids or `GlobalCommand` ids, and in `act` `deploy` and `revert` both or
neither; anything else SHALL refuse the start (`startup/MasterCheck`).

#### Scenario: A misspelled step
- **WHEN** `roles.master.mine` names a word that is no step and no verb
- **THEN** the start is refused, naming the words there are

#### Scenario: Keeping deploy but not revert
- **WHEN** `roles.master.mine` names `deploy` without `revert` in `roles.master.mode: act`
- **THEN** the start is refused: both write the same shared branch
