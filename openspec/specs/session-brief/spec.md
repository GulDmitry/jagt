# session-brief Specification

## Purpose
What the brief asks of a session before its first edit, each ask off until the human turns it on.

## Requirements

### Requirement: Autoplan
With `orchestrator.agent.autoplan.enabled` on, a session SHALL report PLAN_PENDING before its first edit when a criterion holds.
The first line of its `plan.md` SHALL name autoplan, the key and the criterion. The default SHALL be off.

#### Scenario: A large task
- **WHEN** ABC-42 changes more files than `autoplan.files`
- **THEN** the session writes `plan.md`, reports PLAN_PENDING and stops

#### Scenario: Two requirements contradict
- **WHEN** two requirements of ABC-42 contradict
- **THEN** the session plans first and names the contradiction

#### Scenario: A requirement contradicts the code
- **WHEN** a requirement of ABC-42 contradicts what the code guarantees
- **THEN** the session plans first and names the guarantee

#### Scenario: An undefined term
- **WHEN** a term ABC-42 rests on has no definition
- **THEN** the session plans first and names the term

#### Scenario: A decision taken unasked
- **WHEN** the session would settle unasked what changes the code
- **THEN** the session plans first and names the decision

#### Scenario: Turned off
- **WHEN** `autoplan.enabled` is `false` or unset
- **THEN** the session codes as before

### Requirement: Visualize
With `orchestrator.agent.visualize.enabled` on, a session SHALL judge, while analysing its task, whether the task is worth a
picture. When it is, the session SHALL write `task_visual.md` in its worktree before its first edit: a frontend change as an
ASCII sketch of each screen, one fenced block per screen; a backend change as Mermaid and markdown tables.
jagt draws and stores nothing. The default SHALL be off.

#### Scenario: A task worth a picture
- **WHEN** ABC-42 changes a screen and visualize is on
- **THEN** the session writes `task_visual.md` with one fenced block per screen before its first edit

#### Scenario: A two-line fix
- **WHEN** ABC-42 is a two-line bug fix and visualize is on
- **THEN** the session writes no `task_visual.md`

#### Scenario: Turned off
- **WHEN** `visualize.enabled` is `false` or unset
- **THEN** the brief says nothing of a picture and the session works as before

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
