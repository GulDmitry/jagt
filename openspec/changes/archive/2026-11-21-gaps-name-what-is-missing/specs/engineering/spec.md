## RENAMED Requirements

- FROM: `### Requirement: An open gap is listed with its task`
- TO: `### Requirement: An open gap is listed by what is missing`

## MODIFIED Requirements

### Requirement: Layers point one way
A project SHALL keep every dependency pointing inward and every package on the map of its kind, each held by a rule test.
#### Scenario: An inner ring imports an outer one
- **WHEN** a class imports from a ring outside its own **THEN** `rings` fails naming it; the board's JS: ESLint `import-x/no-cycle` and five imports a module at most, no ring rule
#### Scenario: A package is on no kind
- **WHEN** a package sits on no kind of the map or holds three of a kind with no sub-package
- **THEN** `PackagesTest` fails; `KindsTest` holds each kind's one job

### Requirement: Complexity and size are bounded
A method SHALL read at cognitive complexity 12 at most, a class five collaborators at most, and no block of 75 tokens SHALL repeat.
#### Scenario: A method or a class grows
- **WHEN** a method passes the ceiling, a class takes a sixth collaborator or a test wires over five mocks
- **THEN** `cognitive-complexity`, `collaborators-per-class` or `mocks-per-test` fails and ratchets each kind
- **AND** the board's JS: ESLint `complexity` 12 (cyclomatic) and `max-params` 7
#### Scenario: A block is copied
- **WHEN** a block of 75 tokens appears twice **THEN** `duplication` fails; the board's JS: jscpd

### Requirement: No static state and no lifecycle hook that does work
A static field SHALL be final and immutable. Start work SHALL be an event or a job; stop work a lifecycle after the web server.
#### Scenario: A mutable static
- **WHEN** a class holds mutable static state or a `@PostConstruct`
- **THEN** `static-state` or `post-construct-hooks` fails; the board's JS: ESLint refuses a top-level let or var in `npm run lint`

### Requirement: Tests run at every level of the pyramid
A project SHALL pin its behaviour by unit, property, contract, integration, end-to-end, security and performance tests.
#### Scenario: A level is exercised
- **WHEN** the suites run
- **THEN** units and rules run in `test`, with jqwik properties of the task reducer and `WholeApplicationWiringTest` against the OpenAPI contract
- **AND** `OidcLoginOnTomcatTest` and `PasswordLoginRefusalOnTomcatTest` run over a real servlet
- **AND** `e2eTest`, `boardTest` and `standTest` run end to end
- **AND** the stand's server scenarios fail when app start exceeds 15 s or `/state` p95 of 50 calls exceeds 100 ms
#### Scenario: Security tests
- **WHEN** a security requirement of this capability is met
- **THEN** a named test pins it, such as `AgentDoorFilterTest`, `LoopbackFilterTest`, `BoardWriteFilterTest`, `RefusedRequestsTest`, `MasterTokenTest`
#### Scenario: The board's own tests
- **WHEN** the board's JS changes **THEN** its node tests run in `npm test`, CI's `test` job

### Requirement: Every gate stays wired
JaCoCo, PIT, Spotless, Checkstyle, Error Prone, the ArchUnit rule tests, the vulnerability scan and the pins SHALL stay wired into the build or CI.
#### Scenario: A gate is unwired
- **WHEN** a build file or the workflow stops wiring one of them **THEN** `QualityGatesTest` fails naming it; the board's CI step: gap, no test pins it
#### Scenario: A pin is dropped
- **WHEN** a lockfile, the wrapper checksum, an action sha, an image digest or the update bot goes **THEN** `PinningTest` fails naming it

### Requirement: A known vulnerability stops the build
A dependency with a known vulnerability SHALL fail the `test` job of CI, found by osv-scanner on the SBOM of the release jar.
#### Scenario: A dependency is vulnerable
- **WHEN** the scan finds a known vulnerability in a dependency **THEN** the job fails naming it; the board's JS: `npm audit --audit-level=high` fails it

### Requirement: Spotless formats every source set
Spotless SHALL format every source set of every module, tests included, before it compiles.
#### Scenario: Code is unformatted
- **WHEN** a source set is not in google-java-format **THEN** `spotlessApply` rewrites it before `compileJava`; the board's JS: `prettier --check` fails `npm run lint`

### Requirement: Checkstyle gates every source set
Checkstyle SHALL gate every source set of every module, tests included, with no warning allowed.
#### Scenario: Code breaks a style rule
- **WHEN** a source set breaks a Checkstyle rule **THEN** `test` waits for the check and fails; the board's JS: ESLint and strict `tsc` checkJs fail `npm run lint`

### Requirement: Every module keeps a coverage floor
A module SHALL keep 80 percent line coverage, the root's floor its measured ratio.
#### Scenario: A module falls under the floor
- **WHEN** a module's line ratio is under its floor **THEN** `test` fails naming the module and its ratio (JaCoCo); the board's JS: c8, 80 percent lines per file `.c8rc.json` lists

### Requirement: Every test run ends in mutants on the diff
Every test run SHALL end in mutants on the diff, each survivor judged.
#### Scenario: A changed line is mutated
- **WHEN** a main line changes **THEN** `pitestDiff` ends every `test` listing the survivors on it; the board's JS: Stryker on demand, gap per diff

### Requirement: An open gap is listed by what is missing
A gate no stack meets yet SHALL be listed here naming what is missing, never silent, and the work closing it SHALL write the gate above.
#### Scenario: Metrics and build info
- **WHEN** the owner reads the running process **THEN** `/actuator/metrics` and `/actuator/info` answer, `BoardOnTomcatTest` pins them
#### Scenario: Upgrade
- **WHEN** a newer minor replaces an older one
- **THEN** no gap is left: `StateServiceCompatibilityTest` holds every shipped minor's fixture, `docs/server.md` the rollback
#### Scenario: Threat model
- **WHEN** the web surface changes **THEN** `docs/threat-model.md` names it (V15) and `ThreatModelTest` fails on an omitted filter or route
