## MODIFIED Requirements

### Requirement: The head's role marks come from the roles
`/api/tasks` SHALL carry `chips`, each `{key, verb, broken, at, detail}` from a `HeaderChips` bean, never a role's own field.
The Master's `MasterChips` is empty where `roles/master` is absent; `BoardStopChips` and `PendingStartChips` are the board's own.
The board draws each from the catalog, `key.tip` its hover with `detail`, where set, below it; one with a `verb` is a `.stalled` button running it.
The glance reads broken where any chip is `broken`.
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
#### Scenario: A start a restart cut short
- **WHEN** `pending-starts.json` holds `ABC-42` and `tighten the parser`, and no task has that key
- **THEN** `chips` holds one `start-interrupted` entry, `broken`, without a verb, its `detail` naming the key and the words
- **AND** the entry ends once `resume ABC-42` or `resume ABC-42 discard` ran, or a task `ABC-42` exists
