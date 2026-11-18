## MODIFIED Requirements

### Requirement: A round counts only while it answers the report before it
A round SHALL count only while it answers the session report the one before answered.
Every stop SHALL append a line `{at, task, alias, trigger, status, evidence, steps}` to `stops.jsonl` beside
`state.json` (rolled at 1 MB), which `lift` leaves; `steps` is the newest 20 status changes and relayed rounds, newest last.

#### Scenario: A result outlives its round
- **WHEN** ABC-42's probes finish failed after you shipped it
- **THEN** nothing is relayed, reported, recorded or stopped, and the log says `verification outdated`; one on its own round is relayed as before

#### Scenario: A stop is logged
- **WHEN** ABC-42 stops after 22 status changes and 22 relayed rounds
- **THEN** one line of `at`, `task`, `alias`, `trigger`, `status`, `evidence` and `steps` is appended to `stops.jsonl`
