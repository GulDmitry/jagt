## ADDED Requirements

### Requirement: Every gate stays wired
JaCoCo, PIT, Spotless, Checkstyle, Error Prone, the ArchUnit rule tests and the vulnerability scan SHALL stay wired into the commit suite or CI.
#### Scenario: A gate is unwired
- **WHEN** a build file or the workflow stops wiring one of them **THEN** `QualityGatesTest` fails naming it; the board's gates: JAGT-96 checks them

### Requirement: A known vulnerability stops the build
A dependency with a known vulnerability SHALL fail the `test` job of CI, found by osv-scanner on the SBOM of the release jar.
#### Scenario: A dependency is vulnerable
- **WHEN** the scan finds a known vulnerability in a dependency **THEN** the job fails naming it; the board's JS: gap, JAGT-96

### Requirement: Spotless formats every source set
Spotless SHALL format every source set of every module, tests included, before it compiles.
#### Scenario: Code is unformatted
- **WHEN** a source set is not in google-java-format **THEN** `spotlessApply` rewrites it before `compileJava`; the board's JS: Prettier, gap, JAGT-96

### Requirement: Checkstyle gates every source set
Checkstyle SHALL gate every source set of every module, tests included, with no warning allowed.
#### Scenario: Code breaks a style rule
- **WHEN** a source set breaks a Checkstyle rule **THEN** `test` waits for the check and fails; the board's JS: ESLint, gap, JAGT-96

### Requirement: Error Prone compiles every source set
Error Prone with NullAway SHALL compile every source set of every module, tests included, with warnings as errors.
#### Scenario: Code breaks an analysis
- **WHEN** any source set breaks Error Prone or NullAway **THEN** its compile fails; `NullMarkedTest` holds every package marked

### Requirement: Every module keeps a coverage floor
A module SHALL keep 80 percent line coverage, the root's floor its measured ratio.
#### Scenario: A module falls under the floor
- **WHEN** a module's line ratio is under its floor **THEN** `test` fails naming the module and its ratio (JaCoCo); the board's JS: gap, JAGT-96

### Requirement: Every test run ends in mutants on the diff
Every test run SHALL end in mutants on the diff, each survivor judged.
#### Scenario: A changed line is mutated
- **WHEN** a main line changes **THEN** `pitestDiff` ends every `test` listing the survivors on it; the board's JS: gap, JAGT-96

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
- **WHEN** the web surface changes **THEN** no document names its assets and actors: gap, JAGT-133 (ASVS V15)

## MODIFIED Requirements

### Requirement: Layers point one way
A project SHALL keep every dependency pointing inward and every package on the map of its kind, each held by a rule test.
#### Scenario: An inner ring imports an outer one
- **WHEN** a class imports from a ring outside its own **THEN** `RingsTest` fails naming it; the board's JS: gap, JAGT-96
#### Scenario: A package is on no kind
- **WHEN** a package sits on no kind of the map or holds three of a kind with no sub-package
- **THEN** `PackagesTest` fails; `KindsTest` holds each kind's one job

### Requirement: Complexity and size are bounded
A method SHALL read at cognitive complexity 12 at most, a class five collaborators at most, and no block of 75 tokens SHALL repeat.
#### Scenario: A method or a class grows
- **WHEN** a method passes the ceiling, a class takes a sixth collaborator or a test wires over five mocks
- **THEN** `CognitiveComplexityTest`, `CollaboratorCeilingTest` or `MockCeilingTest` fails; `ComplexityCeilingsTest` ratchets each kind
- **AND** the board's JS: gap, JAGT-96
#### Scenario: A block is copied
- **WHEN** a block of 75 tokens appears twice **THEN** `DuplicationTest` fails

### Requirement: Tests run at every level of the pyramid
A project SHALL pin its behaviour by unit, property, contract, integration, end-to-end, security and performance tests.
#### Scenario: A level is exercised
- **WHEN** the suites run
- **THEN** units and rules run in `test`, with jqwik properties of the task reducer and `WholeApplicationWiringTest` against the OpenAPI contract
- **AND** `OidcLoginOnTomcatTest` and `PasswordLoginRefusalOnTomcatTest` run over a real servlet
- **AND** `e2eTest`, `boardTest`, `standTest`, `containerTest` run end to end
#### Scenario: Security tests
- **WHEN** a security requirement of this capability is met
- **THEN** a named test pins it, such as `AgentDoorFilterTest`, `LoopbackFilterTest`, `BoardWriteFilterTest`, `RefusedRequestsTest`, `MasterTokenTest`
#### Scenario: The board's own tests
- **WHEN** the board's JS changes **THEN** its `npm test` is a no-op: gap, JAGT-96

### Requirement: Every suite ends within a ceiling
A suite SHALL end within a ceiling set from its measured time, and a flaky test SHALL be registered or fixed from its cause.
#### Scenario: A suite overruns
- **WHEN** a suite passes its ceiling **THEN** the build fails for the hang (`jagt.suites.gradle`)

### Requirement: Documents stay inside their budgets and link true
A rule, a page, a decision and a spec SHALL stay within their line budgets, and every relative link and anchor SHALL reach its target.
#### Scenario: A document outgrows its budget
- **WHEN** a decision passes 50 lines, a rule 100 or a page 200 **THEN** `TextBudgetTest` fails naming it
#### Scenario: A link is broken
- **WHEN** a relative link or an anchor reaches nothing **THEN** `DocLinksTest` fails

### Requirement: Practices name their checks
A scenario of this capability SHALL name only test classes that exist, and every rule under `docs/rules` SHALL be reached from the
practices page.
#### Scenario: A scenario names a class that is not in the tree
- **WHEN** a scenario of this capability names a test class that exists in no source set **THEN** `EngineeringSpecTest` fails naming it
#### Scenario: A rule is not reached
- **WHEN** a file under `docs/rules` is linked from no row of `docs/practices.md` **THEN** `PracticesPageTest` fails naming it

### Requirement: Headers, cookies and transport are set for a hostile network
The board SHALL answer with a Content-Security-Policy of its own origin; the server starter SHALL set a secure cookie, a session
timeout and forwarded headers; the Ingress SHALL carry a tls block.
#### Scenario: A response leaves the board
- **WHEN** any response leaves the board **THEN** `BoardAssetsTest` and `BoardVendorLoadTest` hold the policy and the vendored files
#### Scenario: A server is started behind TLS
- **WHEN** the server starter or the Ingress is read
- **THEN** `ServerStarterTest` and `KubernetesManifestsTest` hold the cookie, the timeout and the tls block; TLS ends at the ingress

### Requirement: Failure is contained and state recovers
A failing dependency SHALL trip a breaker or be waited out, and a malformed request SHALL answer its error shape.
State SHALL be written atomically and read across a minor.
#### Scenario: A dependency fails
- **WHEN** an assistant or a host fails on infrastructure
- **THEN** `AssistantBreakerTest`, `InfraRetryTest` and `RetryPolicyTest` hold the wait; `AbortedConnectionFilterTest` holds an aborted request
#### Scenario: State is read by a newer minor
- **WHEN** a newer minor reads an older state file or a write is torn **THEN** `StateServiceCompatibilityTest` and `AtomicFilesTest` hold the recovery

### Requirement: A release carries its bill of materials and its provenance
A release SHALL attach a CycloneDX SBOM, a signed build provenance and a sha256 file, as the release-artifacts capability states.
#### Scenario: A tag is released
- **WHEN** the release job runs on a tag **THEN** `CiWorkflowTest` holds the SBOM, the attestations and the checksums

## REMOVED Requirements

### Requirement: Static analysis and format hold for all code
**Reason**: split into one requirement per gate
**Migration**: see the requirement per gate

### Requirement: Every module keeps a coverage floor and its mutants judged
**Reason**: split into one requirement per gate
**Migration**: see the requirement per gate

### Requirement: A missing gate names its task
**Reason**: split into one requirement per gate
**Migration**: see the requirement per gate
