## ADDED Requirements

### Requirement: A headless run has a spend ceiling
Every headless run SHALL be held to `orchestrator.assistant.maxCostUsd`, 0.70 unless set. A run that reaches it SHALL
come back as a failure naming the ceiling, its spend booked as for any run.

#### Scenario: A run under the ceiling
- **WHEN** a headless read costs less than `maxCostUsd`
- **THEN** it answers as before

#### Scenario: A run reaches the ceiling
- **WHEN** a headless read reaches `maxCostUsd` before it answers
- **THEN** the read fails naming the spend ceiling and no answer is read from it
