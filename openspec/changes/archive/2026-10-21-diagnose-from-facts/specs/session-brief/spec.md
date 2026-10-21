## ADDED Requirements

### Requirement: Diagnose
With `orchestrator.agent.diagnose.enabled` on, a session SHALL diagnose a bug from facts before code: gather the report's
evidence and a control, the same case working or failing louder for a known cause, and cite each fact a cause fits.
A repro SHALL count only when it shows the report's signal. The default SHALL be off.

#### Scenario: A bug task
- **WHEN** ABC-42 is a bug and diagnose is on
- **THEN** the session gathers the report's facts and a control before reading code for a cause

#### Scenario: A repro showing another signal
- **WHEN** a repro of ABC-42 fails, but not with the signal the report names
- **THEN** it does not count as the bug's repro

#### Scenario: Turned off
- **WHEN** `diagnose.enabled` is `false` or unset
- **THEN** the brief says nothing of diagnosing and the session works as before
