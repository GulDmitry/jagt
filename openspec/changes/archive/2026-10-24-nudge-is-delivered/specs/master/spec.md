## MODIFIED Requirements

### Requirement: Instructions never land inside your line
A nudge to a session SHALL wait while the human is typing in its window; one left unsent in the input SHALL be
submitted again, three times at most, then reported failed with that cause. It SHALL name no author.

#### Scenario: You are typing
- **WHEN** instructions arrive mid-line
- **THEN** they wait for your line
- **AND** a nudge still in the input after the turn ended gets Enter again; after three the relay says it failed
