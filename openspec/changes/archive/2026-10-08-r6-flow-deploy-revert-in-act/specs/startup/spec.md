## MODIFIED Requirements

### Requirement: The steps the Master leaves you are checked at start
`master.mine` SHALL name only `task/MasterRight` steps, and in `act` `deploy` and `revert` both or neither; anything
else SHALL refuse the start (`startup/MasterCheck`).

#### Scenario: A misspelled step
- **WHEN** `master.mine` names a step `task/MasterRight` lacks
- **THEN** the start is refused, naming the steps there are

#### Scenario: Keeping deploy but not revert
- **WHEN** `master.mine` names `deploy` without `revert` in `master.mode: act`
- **THEN** the start is refused: both write the same shared branch
