## MODIFIED Requirements

### Requirement: An unchanged round is relayed once
`TaskStops.round` SHALL keep a digest of the last round brief relayed into a task and the round's note fingerprint:
the sorted pairs of each open thread's link and newest note id, as the host gives them. A brief equal to the digest
SHALL NOT be relayed again, whatever overwrote `task_context.md` in between. Neither SHALL a round with open threads
whose fingerprint equals the last one's while the checks are red in neither. A `lift` SHALL forget both.

#### Scenario: A ship between two equal reads
- **WHEN** ABC-42 ships and the next poll reads the same threads as the round before
- **THEN** the sweep says the round is unchanged and no fresh session starts

#### Scenario: A reviewer's concession is not a loop
- **WHEN** a thread's newest note is the reviewer's and two polls read it unchanged, the copied text differing
- **THEN** only the first poll relays, and the second says the round is unchanged

#### Scenario: A new note is relayed
- **WHEN** a poll reads a newest note id on a thread that the round before did not carry
- **THEN** the round is relayed
