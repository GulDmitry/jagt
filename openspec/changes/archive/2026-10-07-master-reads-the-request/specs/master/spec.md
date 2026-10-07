## MODIFIED Requirements

### Requirement: A verdict rests on proof
The Master SHALL read the ticket (else `task_request.md`) and the diff. It SHALL prove by a run any premise its verdict rests on, never
from the author.

#### Scenario: The reviewer cannot prove a finding
- **WHEN** a finding cannot be proven
- **THEN** it asks the session to `show:` it, settling nothing

#### Scenario: The session thinks a finding wrong
- **WHEN** the session disagrees with a finding
- **THEN** it writes `disputed:` with evidence in `task_notes.md`; proven, it reopens even a settled decision
