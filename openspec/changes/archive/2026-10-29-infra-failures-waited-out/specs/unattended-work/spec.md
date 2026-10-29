## MODIFIED Requirements

### Requirement: A task has a ceiling of unattended moves an hour
jagt SHALL count, per task, its unasked moves in the last hour since its last `lift`:
history steps by the Master, auto-review, the tracker or a retry. A board press or a session's own report SHALL NOT count.
At `loopBreaker.perHour` (default 20) the next Master press, conflict deploy,
auto-review poll or round SHALL NOT be made. The task SHALL stop, its status kept, as `PACE`. A human press SHALL never be refused.

#### Scenario: Too many moves in an hour
- **WHEN** ABC-42 made 20 unattended moves in the last hour
- **THEN** the next is not made and the card asks `lift the stop`
- **AND** with some of the 20 over an hour old, or before the last lift, the next is made
