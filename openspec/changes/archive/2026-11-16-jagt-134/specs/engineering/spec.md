## ADDED Requirements

### Requirement: Layers point one way
A project SHALL keep every dependency pointing inward and every package on the map of its kind, each held by a rule test.
#### Scenario: An inner ring imports an outer one
- **WHEN** a class imports from a ring outside its own
- **THEN** `RingsTest` fails naming it; the board's JS: gap, JAGT-96
#### Scenario: A package is on no kind
- **WHEN** a package sits on no kind of the map or holds three of a kind with no sub-package
- **THEN** `PackagesTest` fails; `KindsTest` holds each kind's one job

### Requirement: Complexity is bounded
A method SHALL read at cognitive complexity 12 at most, and a kind's class count SHALL stay under its ratcheted ceiling.
#### Scenario: A method grows
- **WHEN** a method passes the ceiling
- **THEN** `CognitiveComplexityTest` fails; `ComplexityCeilingsTest` ratchets each kind; the board's JS: gap, JAGT-96

### Requirement: A class stays small and original
A class SHALL have three collaborators as the target and five as the ceiling, and no block of 75 tokens SHALL repeat.
#### Scenario: A class gathers collaborators
- **WHEN** a class takes a sixth collaborator or a test wires over five mocks
- **THEN** `CollaboratorCeilingTest` or `MockCeilingTest` fails
#### Scenario: A block is copied
- **WHEN** a block of 75 tokens appears twice
- **THEN** `DuplicationTest` fails

### Requirement: Static analysis and format hold for all code
Checkstyle, Error Prone with NullAway and Spotless SHALL gate every source set of every module, tests included.
#### Scenario: Code breaks a check
- **WHEN** any source set breaks Checkstyle, Error Prone, NullAway or the formatter
- **THEN** its compile or `check` task fails; `NullMarkedTest` holds every package marked
#### Scenario: The board's JS
- **WHEN** the board's JS is unformatted or breaks a lint rule
- **THEN** Prettier and ESLint are configured and run by no gate: gap, JAGT-96

### Requirement: No static state and no lifecycle hook that does work
A static field SHALL be final and immutable, start work SHALL be an event or a job, and stop work a lifecycle after the web server.
#### Scenario: A mutable static
- **WHEN** a class holds mutable static state or a `@PostConstruct`
- **THEN** `StaticStateTest` or `LifecycleHooksTest` fails; the board's JS: ESLint refuses a top-level let or var, run by no gate (JAGT-96)

### Requirement: Every module keeps a line-coverage floor
A module SHALL keep 80 percent line coverage, the root's floor being its measured ratio until its launch loop is tested.
#### Scenario: A module falls under the floor
- **WHEN** a module's line ratio is under its floor
- **THEN** `test` fails naming the module and its ratio (JaCoCo); the board's JS: gap, JAGT-96

### Requirement: Mutants are judged on the diff
Every test run SHALL end in mutants on the changed main lines, each survivor judged before the commit.
#### Scenario: A test run ends
- **WHEN** `test` ends
- **THEN** `pitestDiff` ran on the diff against `HEAD`; the board's JS: gap

### Requirement: Tests run at every level of the pyramid
A project SHALL pin its behaviour by unit, property, contract, integration, end-to-end, security and performance tests.
#### Scenario: A level is exercised
- **WHEN** the suites run
- **THEN** units and rules run in `test`, with jqwik properties of the task reducer and `WholeApplicationWiringTest` against the OpenAPI contract
- **AND** `OidcLoginOnTomcatTest` and `PasswordLoginRefusalOnTomcatTest` run over a real servlet; `e2eTest`, `boardTest`, `standTest`, `containerTest` end to end
#### Scenario: Security tests
- **WHEN** a security requirement of this capability is met
- **THEN** a named test pins it, such as `AgentDoorFilterTest`, `LoopbackFilterTest`, `BoardWriteFilterTest`, `RefusedRequestsTest`, `MasterTokenTest`
#### Scenario: The board's own tests
- **WHEN** the board's JS changes
- **THEN** its `npm test` is a no-op: gap, JAGT-96

### Requirement: A test is held to the standard of its kind
A test SHALL reach an assertion, build a class through production's constructor and share only a builder, a fake or a scanner.
#### Scenario: A test breaks the standard
- **WHEN** a test asserts nothing, adds a helper method or a second constructor, or names no main class
- **THEN** `EveryTestAssertsTest`, `NoHelperMethodTest`, `TestBuilderLogicTest`, `ProductionConstructorTest` or `TestNamingTest` fails

### Requirement: Every suite ends within a ceiling
A suite SHALL end within a ceiling set from its measured time, and a flaky test SHALL be registered or fixed from its cause.
#### Scenario: A suite overruns
- **WHEN** a suite passes its ceiling
- **THEN** the build fails for the hang (`jagt.suites.gradle`)

### Requirement: Documents stay inside their budgets and link true
A rule, a page, a decision and a spec SHALL stay within their line budgets, and every relative link and anchor SHALL reach its target.
#### Scenario: A document outgrows its budget
- **WHEN** a decision passes 50 lines, a rule 100 or a page 200
- **THEN** `TextBudgetTest` fails naming it
#### Scenario: A link is broken
- **WHEN** a relative link or an anchor reaches nothing
- **THEN** `DocLinksTest` fails

### Requirement: A decision and a feature each have a home
An architectural decision SHALL have its record, a spec capability its `docs/features.md` line, and a spec its purpose.
#### Scenario: A record is unindexed
- **WHEN** a decision record is missing from the index or a capability from the features page
- **THEN** `DecisionIndexTest` or `FeaturesTest` fails; `SpecLimitsTest` holds each spec's purpose and requirement length

### Requirement: Practices name their checks
A scenario of this capability SHALL name only test classes that exist, and every rule under `docs/rules` SHALL be reached from the
practices page.
#### Scenario: A scenario names a class that is not in the tree
- **WHEN** a scenario of this capability names a test class that exists in no source set
- **THEN** `EngineeringSpecTest` fails naming it
#### Scenario: A rule is not reached
- **WHEN** a file under `docs/rules` is linked from no row of `docs/practices.md`
- **THEN** `PracticesPageTest` fails naming it

### Requirement: Agents act only where a deterministic gate allows
A task SHALL open only from a command or tracker intake, a third party SHALL be a seam chosen in configuration, and no vendor SHALL be
named in code.
#### Scenario: A model opens a task
- **WHEN** a model's answer would open a task
- **THEN** `TaskOpeningDoorsTest` fails; `SeamSelectionTest` and `VendorNamesTest` hold the seams; the session brief stays in `TextBudgetTest`

### Requirement: A write to the board carries its origin
A write to the board SHALL carry the page's Origin, and a peer other than loopback SHALL authenticate.
#### Scenario: A foreign page writes
- **WHEN** a request without the board's Origin writes
- **THEN** `BoardWriteFilterTest` and `OriginFilterTest` hold it refused; `LoopbackFilterTest` holds a remote peer without a login refused

### Requirement: Authorization is one role and one door
An agent door SHALL resolve its caller and refuse another, and every route but the health probe SHALL need the owner.
#### Scenario: An agent calls a tool
- **WHEN** a caller reaches the agent door without its token
- **THEN** `AgentDoorFilterTest` and `McpProtocolServiceTest` hold it refused; `ToolGateTest` holds the tool gate

### Requirement: Identity is a password hash or an issuer
A password SHALL be stored as a BCrypt hash read from an environment name, an issuer's subject SHALL be allow-listed, and no default
credential SHALL exist.
#### Scenario: Auth is none off loopback
- **WHEN** `auth` is `none` on a non-loopback bind
- **THEN** `AuthCheckTest` fails the start; `OidcLoginOnTomcatTest` holds the issuer login, `MasterTokenTest` the token comparison

### Requirement: The session, its guesses and its audit trail are owned
Logout, a back-off on guessed passwords and one structured line per login SHALL stay the board-security capability's requirements.
#### Scenario: Logout, back-off and audit
- **WHEN** an owner logs out, a peer guesses, or a login succeeds or fails
- **THEN** `PasswordLoginRefusalOnTomcatTest`, `LoginBackoffFilterTest` and `PasswordLoginOutcomeTest` hold the board-security scenarios

### Requirement: Headers, cookies and transport are set for a hostile network
The board SHALL answer with a Content-Security-Policy of its own origin; the server starter SHALL set a secure cookie, a session
timeout and forwarded headers; the Ingress SHALL carry a tls block.
#### Scenario: A response leaves the board
- **WHEN** any response leaves the board
- **THEN** `BoardAssetsTest` and `BoardVendorLoadTest` hold the policy and the vendored files
#### Scenario: A server is started behind TLS
- **WHEN** the server starter or the Ingress is read
- **THEN** `ServerStarterTest` and `KubernetesManifestsTest` hold the cookie, the timeout and the tls block; TLS ends at the ingress

### Requirement: Management is exposed as little as possible
Only the health probes SHALL be public, and a secret SHALL be named by its environment key, never held in a file or a log.
#### Scenario: A secret reaches a log
- **WHEN** a log line would carry a password, a secret or a token
- **THEN** `LogbackTargetTest` and `FailureCausesTest` hold the log shape; no secret key is logged in main (grep, 2026-11-16)

### Requirement: Liveness, readiness and shutdown are probed
The process SHALL answer a startup, a liveness and a readiness probe, and SHALL stop gracefully on a signal.
#### Scenario: A pod starts and stops
- **WHEN** the image starts or receives a signal
- **THEN** `KubernetesManifestsTest` holds the probes; `ShutdownEndingsTest` holds each ending; `ClonesHealthTest` holds readiness

### Requirement: Failure is contained and state recovers
A failing dependency SHALL trip a breaker or be waited out, a malformed request SHALL answer its error shape, and state SHALL be written
atomically and read across a minor.
#### Scenario: A dependency fails
- **WHEN** an assistant or a host fails on infrastructure
- **THEN** `AssistantBreakerTest`, `InfraRetryTest` and `RetryPolicyTest` hold the wait; `AbortedConnectionFilterTest` holds an aborted request
#### Scenario: State is read by a newer minor
- **WHEN** a newer minor reads an older state file or a write is torn
- **THEN** `StateServiceCompatibilityTest` and `AtomicFilesTest` hold the recovery

### Requirement: Every store and loop is bounded
A store SHALL have a ceiling, a headless call a spend ceiling, a loop a bound per task, the JVM a heap sized to the pod, and tasks a cap.
#### Scenario: A bound is reached
- **WHEN** a store, a spend or a loop reaches its bound
- **THEN** it stops naming it; `ImageJvmOptionsTest` holds the heap, `StandSlotsTest` the stand slots, `LoopBreakerTest` the loop

### Requirement: A release carries its bill of materials and its provenance
A release SHALL attach a CycloneDX SBOM, a signed build provenance and a sha256 file, as the release-artifacts capability states.
#### Scenario: A tag is released
- **WHEN** the release job runs on a tag
- **THEN** `CiWorkflowTest` holds the SBOM, the attestations and the checksums

### Requirement: A missing gate names its task
A gate no stack meets yet SHALL be listed here with its task, never silent, and its task SHALL end by writing the gate above.
#### Scenario: Pinning
- **WHEN** a dependency, an action, a base image or the wrapper changes
- **THEN** the lockfile, the SHA pins, the digests and the update bot are gap, JAGT-124 (12F II, SLSA integrity, update automation)
#### Scenario: Vulnerabilities
- **WHEN** a dependency has a known vulnerability
- **THEN** no scan stops the build: gap, JAGT-120 (ASVS V13)
#### Scenario: Metrics and build info
- **WHEN** the owner reads the running process
- **THEN** no metrics or build info endpoint answers: gap, JAGT-130
#### Scenario: Time behaviour
- **WHEN** the image starts or `/state` answers
- **THEN** no test asserts a budget: gap, JAGT-131 (25010 time behaviour, performance tests)
#### Scenario: Upgrade
- **WHEN** a newer minor replaces an older one
- **THEN** state holds one minor's fixture and no page says how to roll back: gap, JAGT-132 (25010 replaceability)
#### Scenario: Threat model
- **WHEN** the web surface changes
- **THEN** no document names its assets and actors: gap, JAGT-133 (ASVS V15)

### Requirement: Rows outside jagt's scope are named
A row of a standard SHALL be listed as not applicable with its why, and a rejected one with its decision.
#### Scenario: A row does not apply
- **WHEN** a standard asks for reusability, HSTS, MFA, WebRTC, stateless processes or scale out
- **THEN** it is out: an application, TLS ends at the ingress, one owner account, no WebRTC, state on `/data` by design, scale out rejected by 0179
