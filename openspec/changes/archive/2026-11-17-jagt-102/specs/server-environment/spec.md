## ADDED Requirements

### Requirement: The agent's home is on the volume
The image SHALL set `HOME` to `/data/home`, created at every start, so a session's conversation and login outlive the container.

#### Scenario: A pod is replaced
- **WHEN** the pod is deleted and a new one starts on the same volume
- **THEN** a revived session finds its conversation under `HOME`
