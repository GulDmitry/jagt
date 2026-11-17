## MODIFIED Requirements

### Requirement: Instructions never land inside your line
A nudge SHALL wait while the human types in the window; one left in the input SHALL get Enter again, three times
at most, then be reported failed, and five more while idle, per turn. It SHALL name no author. Waits: `agent.submitWait`, `agent.draftPoll`.

#### Scenario: You are typing
- **WHEN** instructions arrive mid-line
- **THEN** they wait for your line
- **AND** a nudge left in the input gets Enter again, even once idle; after three the relay says it failed; a line that is yours, never
