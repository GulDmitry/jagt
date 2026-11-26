## MODIFIED Requirements

### Requirement: The stop is one mark on the board's head
The header SHALL carry one `.chip.paused` `‖`, dashed, its hover the time of the stop in UTC; pressing it runs `unstop`. It replaces pausing each
started task by hand and wears the pause's mark, for the human chose it; the legend shows it on its own row.
#### Scenario: The mark
- **WHEN** the board is stopped
- **THEN** the header shows `‖ board stopped` as a `.chip.paused` and every card stays readable
#### Scenario: The tab icon
- **WHEN** the board is stopped and nothing else is broken
- **THEN** the tab icon stays unmarked, for a stop the human chose is not a break

### Requirement: The head's role marks come from the roles
`/api/tasks` SHALL carry `chips`, each `{key, verb, kind, at, detail}` from a `HeaderChips` bean, never a role's own field.
`kind` is `NOTE`, `PAUSED` (the human stopped it), `STALLED` (jagt stopped it) or `BAD` (a failure).
The Master's `MasterChips` is empty where `roles/master` is absent; `BoardStopChips` and `PendingStartChips` are the board's own.
The board draws each from the catalog, `key.tip` its hover with `detail`, where set, below it, as `.chip`, `.chip.paused`, `.chip.stalled` or `.chip.bad` by `kind`.
A chip with a `verb` is a button running it. The glance reads broken where any chip is `STALLED` or `BAD`.
#### Scenario: The Master acts
- **WHEN** the Master is installed and its mode is `act`
- **THEN** `chips` holds one `NOTE` entry without a verb, and the head shows its plain chip
#### Scenario: The Master is not installed
- **WHEN** `roles/master` is absent
- **THEN** `chips` holds no Master entry and the head shows none
#### Scenario: Act stopped itself
- **WHEN** three bad endings stopped act
- **THEN** `chips` holds a `STALLED` entry with verb `act`, drawn `.chip.stalled` `■`, and pressing its button runs `act`
#### Scenario: The board is stopped
- **WHEN** the human stopped the board
- **THEN** `chips` holds a `PAUSED` entry with verb `unstop` and the moment of the stop, installed Master or not, drawn `.chip.paused` `‖`
#### Scenario: A start a restart cut short
- **WHEN** `pending-starts.json` holds `ABC-42` and `tighten the parser`, and no task has that key
- **THEN** `chips` holds one `start-interrupted` entry, `BAD`, without a verb, its `detail` naming the key and the words, drawn `.chip.bad`
- **AND** the entry ends once `resume ABC-42` or `resume ABC-42 discard` ran, or a task `ABC-42` exists
