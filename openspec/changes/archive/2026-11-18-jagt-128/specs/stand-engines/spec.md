## ADDED Requirements

### Requirement: A stop is an event of the stand
The stand SHALL stop jagt with SIGTERM on every engine and read `state.json` before anything revives it.

#### Scenario: SIGTERM with a session working
- **WHEN** jagt gets SIGTERM, by `docker compose kill` under compose, the process signal on `host`, or the pod scaled away under `kind`
- **THEN** `state.json` holds the task with its session gone
