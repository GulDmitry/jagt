# stand-engines Specification

## Purpose
Where the stand starts jagt, on the host, under compose or in a kind cluster, and the restarts it applies.

## Requirements

### Requirement: The stand runs on an engine chosen by name
`standTest` SHALL start jagt on the engine named by `-Pengine`: `host` the staged jar, `compose` jagt's image under docker compose,
`kind` jagt's image in a Kubernetes cluster applying `examples/kubernetes.yml`. Without it the engine SHALL be `compose`.
A missing docker SHALL skip the run naming it. An unknown name SHALL fail the run naming the three.

#### Scenario: An engine is named
- **WHEN** `standTest -Pengine=kind` runs
- **THEN** each server scenario starts jagt in a cluster of its own, built for that run and deleted at its end, failure included

#### Scenario: A cluster that cannot be deleted
- **WHEN** `kind delete cluster` fails or passes its limit at the end of a run
- **THEN** the run fails naming the cluster and the exit, and its root stays for the next run start to delete the cluster

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

### Requirement: A stop is an event of the stand
The stand SHALL stop jagt with SIGTERM on every engine and read `state.json` before anything revives it.

#### Scenario: SIGTERM with a session working
- **WHEN** jagt gets SIGTERM, by `docker compose kill` under compose, the process signal on `host`, or the pod scaled away under `kind`
- **THEN** `state.json` holds the task with its session gone

### Requirement: The stand records its timings as a trend
The server scenario SHALL write startup seconds and `/state` p95 in milliseconds, the median of five rounds of 50 calls.
Each is a `customSmallerIsBetter` series per runner label and engine, written whether or not the absolute budgets hold.
The CI stand job SHALL keep the history in the Actions cache, never a pushed branch.
It SHALL compare each series with the median of its last ten history entries and fail naming the series past 200 % of that median.
A series with no history entry SHALL pass.
#### Scenario: A run on a shared runner
- **WHEN** the stand job runs without `-Pbudgets=true` on a runner
- **THEN** `startup <runner>/<engine>` and `state p95 <runner>/<engine>` are written and compared with the median of the history

#### Scenario: A slowdown
- **WHEN** a timing is over 200 % of the median of its last ten history entries
- **THEN** the stand job fails naming that series

#### Scenario: One fast outlier in the history
- **WHEN** the newest history entry is far faster than the rest and a normal run follows
- **THEN** the stand job does not fail
