## ADDED Requirements

### Requirement: The head's role marks come from the roles
`/api/tasks` SHALL carry `chips`, each `{key, verb, broken, at}` from a `HeaderChips` bean: the Master's `MasterChips` (empty where `roles/master`
is absent) and the board's own `BoardStopChips`. The board draws one `.chip` per entry from the catalog (`key`, the hover `key.tip`), a button
wearing `.stalled` that runs `verb` where one is set, and the glance reads broken for any chip with `broken`. The payload has no `masterMode` and no `actStop`.

#### Scenario: The Master acts
- **WHEN** the Master is installed and its mode is `act`
- **THEN** `chips` holds one entry without a verb, and the head shows its plain chip

#### Scenario: The Master is not installed
- **WHEN** `roles/master` is absent
- **THEN** `chips` holds no Master entry and the head shows none

#### Scenario: Act stopped itself
- **WHEN** three bad endings stopped act
- **THEN** `chips` holds an entry with verb `act` and `broken`, and pressing its button runs `act`

#### Scenario: The board is stopped
- **WHEN** the human stopped the board
- **THEN** `chips` holds an entry with verb `unstop` and the moment of the stop, installed Master or not
