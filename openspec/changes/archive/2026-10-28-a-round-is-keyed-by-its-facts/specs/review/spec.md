## MODIFIED Requirements

### Requirement: An unchanged round is relayed once
`TaskStops.round` SHALL keep a digest of the last round relayed into a task: the failing job and the pushed branch tip from git while the checks are
red, and each open thread's link and newest note id. A round equal to it SHALL NOT be relayed again, whatever the brief's wording; where git cannot
name the tip, or a job or note id is unknown, the brief is digested. Neither SHALL open threads with the last notes while the checks are red in
neither. A `lift` SHALL forget both. While the last report asks the human, a sweep SHALL relay nothing, starting no session.

#### Scenario: A ship between two equal reads
- **WHEN** ABC-42 ships and the next poll reads the same threads as the round before
- **THEN** the sweep says the round is unchanged and no fresh session starts

#### Scenario: A reviewer's concession is not a loop
- **WHEN** a thread's newest note is the reviewer's and two polls read it unchanged, the copied text differing
- **THEN** only the first poll relays, and the second says the round is unchanged

#### Scenario: The same failure in other words
- **WHEN** the same job fails on the same pushed tip
- **THEN** the round is not sent again, whatever the brief's wording

#### Scenario: Red checks over a question
- **WHEN** ABC-42's session asked the human and the checks are still red
- **THEN** the sweep relays nothing until the human answers
