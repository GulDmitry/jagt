## ADDED Requirements

### Requirement: A post the delivery backlog rejects is retried
A post the full delivery backlog refuses SHALL count as a channel that cannot be reached. It stays pending.
One WARN and one board-head mark name channel, point and cause `channel backed up`. It is retried on the same backoff until it lands.
#### Scenario: The backlog is full
- **WHEN** a point is posted while the delivery backlog is full
- **THEN** it is not dropped; one board-head mark names the channel and the cause
- **AND** it is retried after 10 s and delivered once the backlog accepts it
