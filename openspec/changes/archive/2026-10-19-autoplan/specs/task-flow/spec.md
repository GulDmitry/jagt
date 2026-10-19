## ADDED Requirements

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
