## MODIFIED Requirements

### Requirement: A read lands only on the generation it was taken at
A fact read from outside SHALL carry the generation it was taken at, as its read began: the session's start count, the head tip,
the requests linked or the deploy commit.
The reducer MUST drop one the task has since moved past, state unchanged, and log `read dropped stale` with its kind.
#### Scenario: A sign read before the session started over
- **WHEN** a read of ABC-42's session lands after the session started over
- **THEN** it is dropped, the new session's record unchanged, and the next read takes its place
