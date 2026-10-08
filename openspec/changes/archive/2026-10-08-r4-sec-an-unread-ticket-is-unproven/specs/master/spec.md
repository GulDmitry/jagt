## MODIFIED Requirements

### Requirement: A verdict rests on proof
The Master SHALL read the ticket (else `task_request.md`) and the diff, or a `PLAN_PENDING` task's `plan.md` in one read
(`MasterPanel.plan`). It SHALL prove each premise by a run, never from the author; an unread ticket proves nothing.

#### Scenario: The reviewer cannot prove a finding
- **WHEN** a finding cannot be proven
- **THEN** it asks the session to `show:` it, settling nothing

#### Scenario: The session thinks a finding wrong
- **WHEN** the session disagrees with a finding
- **THEN** it writes `disputed:` with evidence in `task_notes.md`; proven, it reopens even a settled decision
