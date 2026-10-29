## MODIFIED Requirements

### Requirement: A red round goes back to the session
`FlowRules.readLands` SHALL send the task back to its session and relay the failing job's lines as `<checks>`, a clue; read twice they MUST read the
same, or every poll re-briefs. A red the host calls infrastructure is not relayed: [infra-wait](../infra-wait/spec.md).

#### Scenario: Pipeline goes red
- **WHEN** the pipeline goes red
- **THEN** `checks failed` on the card, one notification; the session finds and fixes the cause

#### Scenario: Red, no comments
- **WHEN** checks are red, no comments
- **THEN** the agent fixes the build and hands back

#### Scenario: Red the session cannot reproduce
- **WHEN** the red is not in the code, or nothing here reads it, and the host does not call it infrastructure
- **THEN** the session changes nothing and asks you, naming the job
