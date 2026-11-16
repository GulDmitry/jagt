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

### Requirement: The image keeps its tmux across a jagt restart
The shipped image SHALL run an init as PID 1 and jagt under a relaunch loop.
When a signal kills jagt's process, the loop SHALL start it again and the container's tmux server SHALL keep running;
any other exit SHALL end the container with jagt's exit code.
A termination signal to the container SHALL stop jagt and the loop promptly.

#### Scenario: The JVM dies
- **WHEN** jagt's JVM inside the container is killed while a session's tmux window is open
- **THEN** jagt starts again in the same container and the window is still listed

#### Scenario: jagt exits on its own
- **WHEN** jagt's JVM exits with a code of its own, a failed start included
- **THEN** the container exits with that code and its restart policy decides what follows

#### Scenario: The container is stopped
- **WHEN** the container receives SIGTERM
- **THEN** jagt stops and the container exits without the loop starting jagt again

### Requirement: Pod liveness
`/actuator/health/liveness` SHALL answer 200 while the application's own state is correct and 503 once it is broken.
It SHALL be a public path of `contracts/openapi.yaml`, answered with no login and from a non-loopback address.
It SHALL NOT depend on a project clone, the start's checks or any system outside jagt.

#### Scenario: A clone is running
- **WHEN** a project's clone runs and nobody is logged in
- **THEN** the liveness path answers 200
