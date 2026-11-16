# readiness Specification

## Purpose

When a container platform may send traffic to jagt: the probe it asks, and what holds it unready.

## Requirements

### Requirement: Pod readiness
`/actuator/health/readiness` SHALL answer 503 until the start's checks passed and while a project clone runs, 200 otherwise.
It SHALL be a public path of `contracts/openapi.yaml`, answered with no login and from a non-loopback address.
A clone that failed SHALL NOT hold it unready; the board's head shows it.

#### Scenario: The start's checks are not done
- **WHEN** the checks of the start have not finished
- **THEN** the readiness path answers 503

#### Scenario: A clone is running
- **WHEN** a project's clone runs
- **THEN** the readiness path answers 503

#### Scenario: Started and cloned
- **WHEN** the checks passed and no clone runs, and nobody is logged in
- **THEN** the readiness path answers 200
