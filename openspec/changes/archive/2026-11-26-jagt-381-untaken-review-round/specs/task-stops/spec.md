## ADDED Requirements

### Requirement: An untaken round is relayed again
The round brief SHALL ask the session to name `round <tag>` in its report's message. The tag is the head of the round's digest,
none where a fact the round turns on is unknown. A round SHALL stay untaken until a report names its tag or one of its note ids.
A newer round or a `lift` also ends it. A sweep reading an untaken round again SHALL relay it and add no round to the trail.
#### Scenario: A round relayed while the session was mid-turn
- **WHEN** ABC-42's round is relayed, the session reports the round before without naming it, and the next sweep reads the same round
- **THEN** the sweep relays it again, and the trail still holds one round
#### Scenario: The report names the round
- **WHEN** the session reports with `round <tag>` in its message
- **THEN** the round is answered, and the next sweep reading it says it is unchanged

### Requirement: An instruction carries an untaken round
Any instruction written to the session's context SHALL carry the untaken round's brief beside its own text.
#### Scenario: A ship instruction after an untaken round
- **WHEN** ABC-42 holds an untaken round and a ship instruction is written to its context
- **THEN** the context carries the instruction and the round's brief
