## ADDED Requirements

### Requirement: A decline is read back to QA
A session's `no_changes` reply to a `qa-round` SHALL be read back to QA once the session has replied: QA accepts it, which ends
the check `declined`, or disputes it with the evidence against it. A first dispute SHALL be a question on the DEPLOYED card.
The Master in `act` answers it unless the human keeps answering, else the human does. A second dispute SHALL be a question
only the human answers. After it nothing is read for that deploy.

#### Scenario: QA accepts the reason
- **WHEN** the session answers a `qa-round` with `no_changes` and QA finds no evidence against its reason
- **THEN** the check ends `declined` and the card keeps DEPLOYED with the reason in its hover

#### Scenario: QA disputes once
- **WHEN** QA disputes the session's reason
- **THEN** the card asks the Master in `act`, else the human, and the session hears the answer

#### Scenario: QA disputes again
- **WHEN** the session replies `no_changes` once more and QA disputes it again
- **THEN** the question waits for the human alone and QA reads nothing after it
