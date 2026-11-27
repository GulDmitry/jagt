## MODIFIED Requirements

### Requirement: A red round goes back to the session
`FlowRules.readLands` SHALL send the task back to its session and relay the failing job's lines as `<checks>`, a clue; read twice they MUST read the
same, or every poll re-briefs. A red the host calls infrastructure is not relayed: [infra-wait](../infra-wait/spec.md).
A round read taken before the head moved MUST relay, mark and move nothing ([task-flow](../task-flow/spec.md)).
#### Scenario: Pipeline goes red
- **WHEN** the pipeline goes red
- **THEN** `checks failed` on the card, one notification; the session finds and fixes the cause
- **WHEN** checks are red, no comments
- **THEN** the agent fixes the build and hands back
- **WHEN** the red is not in the code, or nothing here reads it, and the host does not call it infrastructure
- **THEN** the session changes nothing and asks you, naming the job
#### Scenario: A red read taken before a push
- **WHEN** a round read ABC-42's checks red, then the session pushed, and the read lands after the push
- **THEN** it is dropped: nothing is relayed, the status stays, the task does not stop, and the next read of the new head decides
