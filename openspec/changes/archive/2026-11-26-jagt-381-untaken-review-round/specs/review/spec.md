## MODIFIED Requirements

### Requirement: An unchanged round is relayed once
`TaskStops.round` SHALL keep a digest of each round relayed: the failing job and the pushed tip while the checks are red,
and the sorted newest note ids. The host gives those ids (`CodeHost.unresolvedNotes`), never the model.
A round equal to the last one the session answered SHALL NOT be relayed again, whatever the brief's wording,
unless the task reported `PLAN_PENDING` since. A `lift` SHALL forget the digests.
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
