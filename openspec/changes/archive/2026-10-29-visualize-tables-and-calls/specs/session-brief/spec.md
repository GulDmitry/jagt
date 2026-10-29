## MODIFIED Requirements

### Requirement: Visualize
With `orchestrator.agent.visualize.enabled` on, a session SHALL judge, while analysing its task, whether the task is worth a
picture; a new or changed table, or a call between servers, services or a database, always is. When it is, the session SHALL
write `task_visual.md` in its worktree before its first edit: a frontend change as an ASCII sketch of each screen, one fenced
block per screen; a backend change as Mermaid and markdown tables, an ER diagram drawing every reference between the tables,
a sequence diagram every call. jagt draws and stores nothing. The default SHALL be off.

#### Scenario: A task worth a picture
- **WHEN** ABC-42 changes a screen and visualize is on
- **THEN** the session writes `task_visual.md` with one fenced block per screen before its first edit

#### Scenario: New tables
- **WHEN** ABC-42 adds two tables, one referencing the other, and visualize is on
- **THEN** `task_visual.md` holds an ER diagram drawing that reference

#### Scenario: A call between services
- **WHEN** ABC-42 makes one service call another and visualize is on
- **THEN** `task_visual.md` holds a sequence diagram of that call

#### Scenario: A two-line fix
- **WHEN** ABC-42 is a two-line bug fix and visualize is on
- **THEN** the session writes no `task_visual.md`

#### Scenario: Turned off
- **WHEN** `visualize.enabled` is `false` or unset
- **THEN** the brief says nothing of a picture and the session works as before
