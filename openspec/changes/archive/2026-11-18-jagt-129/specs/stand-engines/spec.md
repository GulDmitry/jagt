## MODIFIED Requirements

### Requirement: The stand runs on an engine chosen by name
`standTest` SHALL start jagt on the engine named by `-Pengine`: `host` the staged jar, `compose` jagt's image under docker compose,
`kind` jagt's image in a Kubernetes cluster applying `kubernetes.yml.dist`. Without it the engine SHALL be `compose`.
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
