## MODIFIED Requirements

### Requirement: Layers point one way
A project SHALL keep every dependency pointing inward and every package on the map of its kind, each held by a rule test.
#### Scenario: An inner ring imports an outer one
- **WHEN** a class imports from a ring outside its own **THEN** `rings` fails naming it;
  the board's JS: ESLint `import-x/no-cycle` and five imports a module at most, no ring rule
#### Scenario: A package is on no kind
- **WHEN** a package sits on no kind of the map or holds three of a kind with no sub-package
- **THEN** `PackagesTest` fails; `KindsTest` holds each kind's one job

### Requirement: Every gate stays wired
JaCoCo, PIT, Spotless, Checkstyle, Error Prone, the ArchUnit rule tests, the vulnerability scan and the pins SHALL stay wired into the build or CI.
#### Scenario: A gate is unwired
- **WHEN** a build file or the workflow stops wiring one of them **THEN** `QualityGatesTest` fails naming it;
  the board's CI step: gap, no test pins it
#### Scenario: A pin is dropped
- **WHEN** a lockfile, the wrapper checksum, an action sha, an image digest or the update bot goes **THEN** `PinningTest` fails naming it

### Requirement: A known vulnerability stops the build
A dependency with a known vulnerability SHALL fail the `test` job of CI, found by osv-scanner on the SBOM of the release jar.
#### Scenario: A dependency is vulnerable
- **WHEN** the scan finds a known vulnerability in a dependency **THEN** the job fails naming it;
  the board's JS: `npm audit --audit-level=high` fails it

### Requirement: Spotless formats every source set
Spotless SHALL format every source set of every module, tests included, before it compiles.
#### Scenario: Code is unformatted
- **WHEN** a source set is not in google-java-format **THEN** `spotlessApply` rewrites it before `compileJava`;
  the board's JS: `prettier --check` fails `npm run lint`

### Requirement: Checkstyle gates every source set
Checkstyle SHALL gate every source set of every module, tests included, with no warning allowed.
#### Scenario: Code breaks a style rule
- **WHEN** a source set breaks a Checkstyle rule **THEN** `test` waits for the check and fails;
  the board's JS: ESLint and strict `tsc` checkJs fail `npm run lint`

### Requirement: Every module keeps a coverage floor
A module SHALL keep 80 percent line coverage, the root's floor its measured ratio.
#### Scenario: A module falls under the floor
- **WHEN** a module's line ratio is under its floor **THEN** `test` fails naming the module and its ratio (JaCoCo);
  the board's JS: c8, 80 percent lines per file `.c8rc.json` lists

### Requirement: An open gap is listed by what is missing
A gate no stack meets yet SHALL be listed here by what is missing, never silent. The work closing it SHALL write the gate above.
#### Scenario: Metrics and build info
- **WHEN** the owner reads the running process **THEN** `/actuator/metrics` and `/actuator/info` answer, `BoardOnTomcatTest` pins them
#### Scenario: Upgrade
- **WHEN** a newer minor replaces an older one
- **THEN** no gap is left: `StateServiceCompatibilityTest` holds every shipped minor's fixture, `docs/server.md` the rollback
#### Scenario: Threat model
- **WHEN** the web surface changes **THEN** `docs/threat-model.md` names it (V15) and `ThreatModelTest` fails on an omitted filter or route
