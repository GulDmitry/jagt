## MODIFIED Requirements

### Requirement: Layers point one way
A project SHALL keep every dependency pointing inward and every package on the map of its kind, each held by a rule test.
#### Scenario: An inner ring imports an outer one
- **WHEN** a class imports from a ring outside its own **THEN** `rings` fails naming it; board JS: `import-x/no-cycle`, five imports a module
#### Scenario: A package is on no kind
- **WHEN** a package sits on no kind of the map or holds three of a kind with no sub-package
- **THEN** `PackagesTest` fails; `KindsTest` holds each kind's one job

### Requirement: Every gate stays wired
JaCoCo, PIT, Spotless, Checkstyle, Error Prone, the ArchUnit rule tests, the vulnerability scan and the pins SHALL stay wired into the build or CI.
#### Scenario: A gate is unwired
- **WHEN** a build file or the workflow stops wiring one of them **THEN** `QualityGatesTest` fails naming it; board CI step: gap
#### Scenario: A pin is dropped
- **WHEN** a lockfile, the wrapper checksum, an action sha, an image digest or the update bot goes **THEN** `PinningTest` fails naming it

### Requirement: A known vulnerability stops the build
A dependency with a known vulnerability SHALL fail the `test` job of CI, found by osv-scanner on the SBOM of the release jar.
#### Scenario: A dependency is vulnerable
- **WHEN** the scan finds a known vulnerability in a dependency **THEN** the job fails naming it; board JS: `npm audit`

### Requirement: Spotless formats every source set
Spotless SHALL format every source set of every module, tests included, before it compiles.
#### Scenario: Code is unformatted
- **WHEN** a source set is not in google-java-format **THEN** `spotlessApply` rewrites it before `compileJava`; board JS: Prettier

### Requirement: Checkstyle gates every source set
Checkstyle SHALL gate every source set of every module, tests included, with no warning allowed.
#### Scenario: Code breaks a style rule
- **WHEN** a source set breaks a Checkstyle rule **THEN** `test` waits for the check and fails; board JS: ESLint, `tsc`

### Requirement: Every module keeps a coverage floor
A module SHALL keep 80 percent line coverage, the root's floor its measured ratio.
#### Scenario: A module falls under the floor
- **WHEN** a module's line ratio is under its floor **THEN** `test` fails naming the module and its ratio (JaCoCo); board JS: c8
