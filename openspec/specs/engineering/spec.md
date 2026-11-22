# engineering Specification

## Purpose
Every gate of jagt's engineering practices, stack-agnostic, each naming its check per stack or the task that closes its gap.

## Requirements

### Requirement: Layers point one way
A project SHALL keep every dependency pointing inward and every package on the map of its kind, each held by a rule test.
#### Scenario: An inner ring imports an outer one
- **WHEN** a class imports from a ring outside its own **THEN** `rings` fails naming it; board JS: `import-x/no-cycle`, five imports a module
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
- **THEN** a named test pins it, such as `LoopbackOrRootTokenTest`, `NotThePageTest`, `RefusedRequestsTest`, `MasterTokenTest`
#### Scenario: The board's own tests
- **WHEN** the board's JS changes **THEN** its node tests run in `npm test`, CI's `test` job

### Requirement: A test is held to the standard of its kind
A test SHALL reach an assertion, build a class through production's constructor and share only a builder, a fake or a scanner.
#### Scenario: A test breaks the standard
- **WHEN** a test asserts nothing, adds a helper method or a second constructor, or names no main class
- **THEN** `assertionless-tests`, `test-helper-methods`, `test-builder-calls`, `constructors-called-from-main` or `test-naming` fails

### Requirement: Every run ends within a limit
A suite, a gate and a CI job SHALL end within a limit set from its measured time. A flaky test SHALL be registered or fixed from its cause.
#### Scenario: A suite or a gate overruns
- **WHEN** a run passes its limit **THEN** the build names the rule, run and limit: `suite-minutes`, `gate-minutes-heavy`, `gate-minutes-stand`
#### Scenario: A run declares no limit
- **WHEN** a test task or a CI job declares none
- **THEN** `unlimited-runs` or `ci-jobs-without-timeout` fails

### Requirement: Documents stay inside their budgets and link true
A rule, a page, a decision and a spec SHALL stay within their line budgets, and every relative link and anchor SHALL reach its target.
#### Scenario: A document outgrows its budget
- **WHEN** a decision passes 50 lines, a rule 100 or a page 200 **THEN** `decision-lines`, `rule-lines` or `page-lines` fails naming it
#### Scenario: A link is broken
- **WHEN** a relative link or an anchor reaches nothing **THEN** `DocLinksTest` fails

### Requirement: A decision and a feature each have a home
An architectural decision SHALL have its record, a spec capability its `docs/features.md` line, and a spec its purpose.
#### Scenario: A record is unindexed
- **WHEN** a decision record is missing from the index or a capability from the features page
- **THEN** `DecisionIndexTest` or `FeaturesTest` fails; `SpecLimitsTest` holds each spec's purpose and requirement length

### Requirement: Practices name their checks
A scenario of this capability SHALL name only test classes that exist, and every rule under `docs/rules` SHALL be reached from practices.
#### Scenario: A scenario names a class that is not in the tree
- **WHEN** a scenario of this capability names a test class that exists in no source set **THEN** `EngineeringSpecTest` fails naming it
#### Scenario: A rule is not reached
- **WHEN** a file under `docs/rules` is linked from no row of `docs/practices.md` **THEN** `PracticesPageTest` fails naming it

### Requirement: Agents act only where a deterministic gate allows
A task SHALL open only from a command or tracker intake. A third party SHALL be a seam chosen in configuration; no vendor is named in code.
#### Scenario: A model opens a task
- **WHEN** a model's answer would open a task
- **THEN** `task-opening-doors` fails; `SeamSelectionTest` and `VendorNamesTest` hold the seams; the brief stays in `session-brief-step-lines`

### Requirement: A write to the board carries its origin
A write to the board SHALL carry the page's Origin, a peer other than loopback SHALL authenticate, and an agent door SHALL resolve its caller.
#### Scenario: A foreign page writes
- **WHEN** a request without the board's Origin writes
- **THEN** `NotThePageTest` and `OriginFilterTest` hold it refused; `NotThePageTest` holds a remote peer without a login refused
#### Scenario: An agent calls a tool
- **WHEN** a caller reaches the agent door without its token
- **THEN** `LoopbackOrRootTokenTest` and `McpProtocolServiceTest` hold it refused; `ToolGateTest` holds the tool gate

### Requirement: Identity is a password hash or an issuer
A password SHALL be a BCrypt hash read from an environment name, an issuer's subject allow-listed; no default credential SHALL exist.
#### Scenario: Auth is none off loopback
- **WHEN** `auth` is `none` on a non-loopback bind
- **THEN** `AuthCheckTest` fails the start; `OidcLoginOnTomcatTest` holds the issuer login, `MasterTokenTest` the token comparison

### Requirement: The session, its guesses and its audit trail are owned
Logout, a back-off on guessed passwords and one structured line per login SHALL stay the board-security capability's requirements.
#### Scenario: Logout, back-off and audit
- **WHEN** an owner logs out, a peer guesses, or a login succeeds or fails
- **THEN** `PasswordLoginRefusalOnTomcatTest`, `BackedOffPeersTest` and `PasswordLoginOutcomeTest` hold the board-security scenarios

### Requirement: Headers, cookies and transport are set for a hostile network
The board SHALL answer with a Content-Security-Policy of its own origin; the server starter SHALL set a secure cookie, a session
timeout and forwarded headers; the Ingress SHALL carry a tls block.
#### Scenario: A response leaves the board
- **WHEN** any response leaves the board **THEN** `BoardAssetsTest` and `BoardVendorLoadTest` hold the policy and the vendored files
#### Scenario: A server is started behind TLS
- **WHEN** the server starter or the Ingress is read
- **THEN** `ServerStarterTest` and `KubernetesManifestsTest` hold the cookie, the timeout and the tls block; TLS ends at the ingress

### Requirement: Management is exposed as little as possible
Only the health probes SHALL be public, metrics and build info SHALL answer the owner, a secret SHALL be named by its environment key.
#### Scenario: A secret reaches a log
- **WHEN** a log line would carry a password, a secret or a token
- **THEN** `LogbackTargetTest` and `failure-cause-from-message` hold the log shape; no secret key is logged in main (grep, 2026-11-16)
#### Scenario: The owner reads the running process
- **WHEN** the owner reads `/actuator/metrics` or `/actuator/info` **THEN** `BoardOnTomcatTest` pins both
#### Scenario: The web surface changes
- **WHEN** a filter or a route is added **THEN** `ThreatModelTest` fails on one `docs/threat-model.md` omits (V15)

### Requirement: Liveness, readiness and shutdown are probed
The process SHALL answer a startup, a liveness and a readiness probe, and SHALL stop gracefully on a signal.
#### Scenario: A pod starts and stops
- **WHEN** the image starts or receives a signal
- **THEN** `KubernetesManifestsTest` holds the probes; `ShutdownEndingsTest` holds each ending; `ClonesHealthTest` holds readiness

### Requirement: Failure is contained and state recovers
A failing dependency SHALL trip a breaker or be waited out, and a malformed request SHALL answer its error shape.
State SHALL be written atomically and read across a minor.
#### Scenario: A dependency fails
- **WHEN** an assistant or a host fails on infrastructure
- **THEN** `AssistantBreakerTest`, `InfraRetryTest` and `RetryPolicyTest` hold the wait; `AbortedConnectionFilterTest` holds an aborted request
#### Scenario: State is read by a newer minor
- **WHEN** a newer minor reads an older state file or a write is torn **THEN** `AtomicFilesTest` holds the recovery
- **AND** `StateServiceCompatibilityTest` holds every shipped minor's fixture, `docs/server.md` the rollback

### Requirement: Every store and loop is bounded
A store SHALL have a ceiling, a headless call a spend ceiling and a loop a bound per task. The JVM heap SHALL fit the pod; tasks have a cap.
#### Scenario: A bound is reached
- **WHEN** a store, a spend or a loop reaches its bound
- **THEN** it stops naming it; `ImageJvmOptionsTest` holds the heap, `StandSlotsTest` the stand slots, `LoopBreakerTest` the loop

### Requirement: A release carries its bill of materials and its provenance
A release SHALL attach a CycloneDX SBOM, a signed build provenance and a sha256 file, as the release-artifacts capability states.
#### Scenario: A tag is released
- **WHEN** the release job runs on a tag **THEN** `CiWorkflowTest` holds the SBOM, the attestations and the checksums

### Requirement: Rows outside jagt's scope are named
A row of a standard SHALL be listed as not applicable with its why, and a rejected one with its decision.
#### Scenario: A row does not apply
- **WHEN** a standard asks for reusability, HSTS, MFA, WebRTC, stateless processes or scale out
- **THEN** it is out: an application, TLS ends at the ingress, one owner account, no WebRTC, state on `/data` by design, scale out rejected by 0179

### Requirement: Every gate stays wired
JaCoCo, PIT, Spotless, Checkstyle, Error Prone, the ArchUnit rule tests, the vulnerability scan and the pins SHALL stay wired into the build or CI.
#### Scenario: A gate is unwired
- **WHEN** a build file or the workflow stops wiring one of them **THEN** `QualityGatesTest` fails naming it
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

### Requirement: Error Prone compiles every source set
Error Prone with NullAway SHALL compile every source set of every module, tests included, with warnings as errors.
#### Scenario: Code breaks an analysis
- **WHEN** any source set breaks Error Prone or NullAway **THEN** its compile fails; `unmarked-packages` holds every package marked

### Requirement: Every module keeps a coverage floor
A module SHALL keep 80 percent line coverage, the root's floor its measured ratio.
#### Scenario: A module falls under the floor
- **WHEN** a module's line ratio is under its floor **THEN** `test` fails naming the module and its ratio (JaCoCo); board JS: c8

### Requirement: Every test run ends in mutants on the diff
Every test run SHALL end in mutants on the diff, each survivor judged.
#### Scenario: A changed line is mutated
- **WHEN** a main line changes **THEN** `pitestDiff` ends every `test` listing the survivors on it; the board's JS: Stryker on demand

### Requirement: An open gap is listed by what is missing
A gate no stack meets yet SHALL be listed here by what is missing, never silent, each gap once. The work closing it SHALL write the gate above.
#### Scenario: The open gaps
- **WHEN** `pitestDiff` or the release gate runs **THEN** no rule asserts its limit: gap
- **AND** an unwired board CI step fails no rule: gap
- **AND** a changed board JS line gets Stryker on demand, never per diff: gap
