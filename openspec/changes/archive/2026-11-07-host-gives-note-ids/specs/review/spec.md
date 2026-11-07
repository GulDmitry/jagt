## MODIFIED Requirements

### Requirement: An unchanged round is relayed once
`TaskStops.round` SHALL keep a digest of the last round relayed into a task: the failing job and the pushed branch tip from git
while the checks are red, and the sorted newest note ids awaiting the agent. The host's own read gives those ids
(`PipelineRetry.unresolvedNotes`), never the model; they are compared without the request link. A round equal to the digest SHALL NOT
be relayed again, whatever the brief's wording. Where git cannot name the tip, or a job or note id is unknown, the brief is digested.
A failed host read leaves the ids unknown, with a warning naming the cause. Neither SHALL open threads with the last notes
while the checks are red in neither. A `lift` SHALL forget both.

#### Scenario: A ship between two equal reads
- **WHEN** ABC-42 ships and the next poll reads the same threads as the round before
- **THEN** the sweep says the round is unchanged and no fresh session starts

#### Scenario: A reviewer's concession is not a loop
- **WHEN** a thread's newest note is the reviewer's and two polls read it unchanged, the copied text differing
- **THEN** only the first poll relays, and the second says the round is unchanged

#### Scenario: The same failure in other words
- **WHEN** the same job fails on the same pushed tip
- **THEN** the round is not sent again, whatever the brief's wording

#### Scenario: The model writes other ids
- **WHEN** two polls of one request have the model write different note ids and the host gives the same ones
- **THEN** the round is the same and is relayed once

### Requirement: The unit of a round is a thread
`ReviewFacts.threads` SHALL carry every note, oldest first with its author; the agent answers the newest.
Threads SHALL be relayed while unresolved, the round telling how many notes are new or unchanged since the last round
(`RoundTrail.sinceLast`). The checks SHALL never decide whether a thread is answered. After a round with threads,
a hand-back to REVIEW_PENDING SHALL be refused until `review_replies.md` is written since that relay or a ship ran (`HandBackDue`).
#### Scenario: Notes since the last round
- **WHEN** a second round carries two note ids the last did not and one it did
- **THEN** it says `new notes: 2; still open, unchanged: 1`
