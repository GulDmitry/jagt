## MODIFIED Requirements

### Requirement: Another branch is another task, opened by you
Work needing another branch SHALL be its own task, and the Master SHALL open none: a `do` line in its answer opens
nothing, the session told only you open a task. Tracker intake is yours, configured.

#### Scenario: The Master's answer holds a `do` line
- **WHEN** in `act` it answers a session with `do proj drop the old keys from main`
- **THEN** no task opens; the session names the work in its closing report
