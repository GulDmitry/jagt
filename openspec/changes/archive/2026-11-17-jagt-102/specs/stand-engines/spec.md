## ADDED Requirements

### Requirement: The stand runs on an engine chosen by name
`standTest` SHALL start jagt on the engine named by `-Pengine`: `host` the staged jar, `compose` jagt's image under docker compose,
`kind` jagt's image in a Kubernetes cluster applying `kubernetes.yml.dist`. Without it the engine SHALL be `compose`.
A missing docker SHALL skip the run naming it. An unknown name SHALL fail the run naming the three.

#### Scenario: An engine is named
- **WHEN** `standTest -Pengine=kind` runs
- **THEN** each server scenario starts jagt in a cluster of its own, built for that run and deleted at its end, failure included

#### Scenario: A scenario the engine cannot host
- **WHEN** a scenario reads the run's root and the engine is `kind`, or asks for an image upgrade on `host`
- **THEN** it is skipped naming why, never failed

### Requirement: The server scenarios hold what the container suite held
The server scenarios SHALL start jagt from the shipped server starter with a stub project and log in by password, on every engine.
They SHALL check each of these:
- the board refuses without a login
- a task reaches review, and its diff, session tab and `stop` work
- the agent door refuses without the Master token
- a secret read from a file is not logged
- jagt is ready within 15 s
- `/state` answers at p95 100 ms over 50 calls

#### Scenario: A password login
- **WHEN** a server scenario calls `/state` without a login and with one
- **THEN** the first is redirected and the second answered 200

### Requirement: A restart is an event of the stand
A restart SHALL be one of three: the JVM killed, the container or pod replaced, the image replaced on the old volume.
After each the board SHALL open on the state it held before, and a session SHALL re-enter with `--continue` and keep its conversation.

#### Scenario: The JVM is killed
- **WHEN** jagt's JVM is killed inside its container
- **THEN** it relaunches and its tasks and sessions are back

#### Scenario: The pod is deleted
- **WHEN** the pod is deleted under `kind`, or the container restarts under `compose`
- **THEN** the session revives with `--continue` and its conversation is kept

#### Scenario: A new image on the old volume
- **WHEN** a new image starts on the old volume
- **THEN** the board opens on the old state
