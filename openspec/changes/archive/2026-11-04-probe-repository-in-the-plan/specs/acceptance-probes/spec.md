## MODIFIED Requirements

### Requirement: The Master reads probes as facts
The Master's plan read SHALL quote the probes it ratifies; its round read SHALL quote each result (`MasterPanel`).
Its plan read SHALL flag a probe reaching another repository by a path rather than `repo=`, pattern `misplaced probe`.

#### Scenario: A round carrying probes
- **WHEN** a round reaches the Master
- **THEN** each role reads the probes and the results jagt recorded

#### Scenario: A probe reaching another repository by a path
- **WHEN** a plan of a task with repositories `api` and `web` declares `command npm --prefix ../web test`
- **THEN** the Master's plan read is not ready, naming the probe
