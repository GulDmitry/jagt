## MODIFIED Requirements

### Requirement: An open gap is listed with its task
A gate no stack meets yet SHALL be listed here with its task, never silent, and its task SHALL end by writing the gate above.
#### Scenario: Pinning
- **WHEN** a dependency, an action, a base image or the wrapper changes
- **THEN** the lockfile, the SHA pins, the digests and the update bot are gap, JAGT-124 (12F II, SLSA integrity, update automation)
#### Scenario: Metrics and build info
- **WHEN** the owner reads the running process **THEN** no metrics or build info endpoint answers: gap, JAGT-130
#### Scenario: Time behaviour
- **WHEN** the image starts or `/state` answers **THEN** no test asserts a budget: gap, JAGT-131 (25010 time behaviour, performance tests)
#### Scenario: Upgrade
- **WHEN** a newer minor replaces an older one
- **THEN** state holds one minor's fixture and no page says how to roll back: gap, JAGT-132 (25010 replaceability)
#### Scenario: Threat model
- **WHEN** the web surface changes **THEN** `docs/threat-model.md` names it (V15) and `ThreatModelTest` fails on an omitted filter or route
