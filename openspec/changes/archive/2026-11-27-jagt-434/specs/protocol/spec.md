## MODIFIED Requirements

### Requirement: A hook's post is filtered, not refused
`protocol/SessionHookReport` SHALL filter what a hook posts, never refuse it: what jagt cannot believe is dropped, logged once.
A hook SHALL name the session start it was sent from in `X-Session-Start`, read from its process's environment; its sign lands on that start.
#### Scenario: A relative or unparseable log path
- **WHEN** a hook posts a relative path, or one holding a NUL character
- **THEN** it is dropped as a violation, never thrown, the rest kept
#### Scenario: A hook from a session that has since restarted
- **WHEN** a hook's sign names a session start the task has moved past
- **THEN** the reducer drops it with `read dropped stale`, the task unchanged
- **WHEN** a hook names no start, or one that is no count
- **THEN** its sign lands on the start the task stands at on arrival
