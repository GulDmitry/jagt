## MODIFIED Requirements

### Requirement: The reducer records what the watchdog read
Only the reducer SHALL write what it finds into `TaskState.session`: a dead process at any status, an API error ending a turn,
or a turn end with nothing newer behind it past the threshold: the same flip, a NEEDS YOU line, overruled at `NEW`.
It alerts only where `Move.ownerOf` is AGENT, not `VERIFYING`.

#### Scenario: A repaint after a turn end
- **WHEN** after a turn end the window only prints (a repaint), with no call, tool use or log line
- **THEN** the turn end stands: the card never reads working, its quiet counts from the turn end
