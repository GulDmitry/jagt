# Practices

[← README](../README.md) · The one page a CLI agent reads to copy jagt's engineering practices into a new project.
Each row is a stack-agnostic principle, its rule home, the check pinning it per stack, and the unit to copy.
The gates are requirements in [`engineering`](../openspec/specs/engineering/spec.md); a rule is linked here, never restated.

## Copy prompt

```text
Read docs/practices.md of the jagt repository. For each sphere, adopt the rows that fit my stack: copy the unit named in "copy",
adapt its base package in `SourceRoots.BASE_PACKAGE`, and keep its check beside it.
Class-name literals in `FlowDoorsTest`, `TaskOpeningDoorsTest`, `RoleNamesTest`, `CeilingKind` and `CodeCeilings` are adapted by hand.
Link jagt's rule home, never copy its text.
A check that fails is fixed in the code, never by raising its number. A row saying "gap" is not adopted: list it and ask me.
Report a table: practice | adopted | unit copied | gap. Run my test suite after every sphere.
```

Reading the columns: `NNNN` is `docs/decisions/NNNN-*.md`. "Java" is the backend, "JS" the board's JavaScript.
`gap: JAGT-n` names the open task; `gap` has none yet; `n.a.` is out of scope (the spec says why).
A row's evidence was checked 2026-11-16 against the tree and the ISO/IEC 25010, OWASP ASVS 5 level 2, Spring Boot production-ready,
twelve-factor, SLSA build level 2 and test-pyramid rows it names.

## Architecture and layers

| practice | rule home | Java | JS | copy |
|---|---|---|---|---|
| Dependencies point inward only; ports in the centre (25010 modularity: six Gradle modules) | [rings](../ARCHITECTURE.md#the-rings), [seams](rules/seams.md) | `RingsTest`, `PackagesTest` 0166, `KindsTest` | gap: JAGT-96 | `RingsTest`, `PackagesTest`, `PackageMap` |
| A kind of thing names its standard and its check; its count has a ceiling | [kinds](../ARCHITECTURE.md#the-kinds), 0180 | `KindsTest`, `ComplexityCeilingsTest` 0176 | gap: JAGT-96 | `ComplexityCeilingsTest`, `complexity-ceilings.json` |
| No static state, no lifecycle hook that does work | [components](rules/components.md), 0160, 0169 | `StaticStateTest`, `LifecycleHooksTest` | ESLint top-level let/var, run by no gate: JAGT-96 | the two tests |
| A third party is a seam chosen in config; no vendor in code (25010 adaptability; 12F IV) | [seams](rules/seams.md), 0130 | `SeamSelectionTest`, `VendorNamesTest`, `PlatformCheck` | n.a. | `SeamSelectionTest`, `VendorNamesTest` |
| Configuration is validated at start; secrets by env name (12F III, ASVS V2) | [components](rules/components.md) | `StartupValidationTest`, `ConfigReferenceTest`, `AuthCheckTest` | n.a. | `StartupValidationTest` |
| 25010 reusability | n.a. | an application, no module is consumed elsewhere | n.a. | none |

## Code quality and complexity

| practice | rule home | Java | JS | copy |
|---|---|---|---|---|
| Method at cognitive complexity 12 at most (25010 modifiability) | [style](rules/style.md), 0053 | `CognitiveComplexityTest` | gap: JAGT-96 | `CognitiveComplexityTest` |
| Three collaborators a class, five the ceiling; five mocks a test | [style](rules/style.md) | `CollaboratorCeilingTest`, `MockCeilingTest` | n.a. | the two tests |
| No block of 75 tokens twice | [style](rules/style.md) | `DuplicationTest` | gap | `DuplicationTest` |
| Static analysis and format gate all code, tests included (25010 modifiability, ASVS V15) | [style](rules/style.md), 0143, 0146, 0162 | Checkstyle, Error Prone, NullAway, Spotless in `jagt.conventions.gradle`; `NullMarkedTest` | Prettier, ESLint configured, run by no gate: JAGT-96 | `jagt.conventions.gradle` |
| Value records have withers or a builder, no null soup | [style](rules/style.md) | `WithersTest` | n.a. | `WithersTest` |
| English and UTC only, set in main | [components](rules/components.md) | `EnglishAndUtcTest` | `BoardAssetsTest` | `EnglishAndUtcTest` |

## Tests and the pyramid

| practice | rule home | Java | JS | copy |
|---|---|---|---|---|
| Line coverage floor per module (25010 testability) | [testing](rules/testing.md), 0181 | JaCoCo in `jagt.conventions.gradle`, 0.80 per module | gap: JAGT-96 | `jagt.conventions.gradle` |
| Mutants judged on the diff at every test run (pyramid mutation) | [testing](rules/testing.md), 0125, 0127, 0151 | `pitestDiff`, `jagt.pitest.gradle` | gap | `jagt.pitest.gradle` |
| Unit and rule tests run whole in one gate | [testing-suites](rules/testing-suites.md) | `./gradlew test` | `npm test` is a no-op: JAGT-96 | `jagt.suites.gradle` |
| Property-based (pyramid) | [testing](rules/testing.md) | jqwik properties of the task reducer | gap | one properties class |
| Contract (pyramid, 25010 interoperability) | [testing](rules/testing.md), 0170 | `WholeApplicationWiringTest` against `contracts/openapi.yaml`, `SchemaTest` | `board-events.schema.json` | `WholeApplicationWiringTest` |
| Integration and slice (pyramid) | [testing-suites](rules/testing-suites.md) | `OidcLoginOnTomcatTest`, `PasswordLoginRefusalOnTomcatTest`, `containerTest` | `boardTest` | the two `OnTomcat` tests |
| End to end (pyramid, 12F X parity) | [testing-suites](rules/testing-suites.md) | `e2eTest`, `standTest`, `containerTest`, nightly and on a tag; same scripts in CI | `boardTest` in Chromium | `ci.yml` heavy job |
| Performance and load (pyramid, 25010 time behaviour) | [testing-suites](rules/testing-suites.md), 0139 | suite ceilings only | gap: JAGT-131 | gap: JAGT-131 |
| Security tests (pyramid) | [testing](rules/testing.md) | `AgentDoorFilterTest`, `LoopbackFilterTest`, `BoardWriteFilterTest`, `RefusedRequestsTest`, `MasterTokenTest`; DAST n.a. | gap: JAGT-96 | the filter tests |
| A test reaches an assertion, uses production's constructor, shares only a builder, fake or scanner | [testing](rules/testing.md), 0051, 0163, 0164 | `EveryTestAssertsTest`, `ProductionConstructorTest`, `TestBuilderLogicTest`, `NoHelperMethodTest`, `TestNamingTest` | n.a. | the five tests |
| A suite ends within a ceiling; a flaky test is registered or fixed (25010 maturity) | [testing](rules/testing.md), 0139 | `jagt.suites.gradle`, `jagt.suite-stats.gradle` | n.a. | `jagt.suites.gradle` |
| A test never reaches the developer's terminal | [testing](rules/testing.md), 0058 | `TestTmuxCleanupTest`, `SuitesTapNobodyTest` | n.a. | `TestTmuxCleanupTest` |

## Gates and CI

| practice | rule home | Java | JS | copy |
|---|---|---|---|---|
| Three groups: unit+functional every commit, heavy nightly and on a tag, stand before a release | [testing-suites](rules/testing-suites.md) | `ci.yml`, `CiWorkflowTest` | `boardTest` in the heavy group | `ci.yml` |
| A release builds on a hosted runner from the tag (12F V, SLSA L2 build) | [testing-suites](rules/testing-suites.md) | `ci.yml` release job, `CiWorkflowTest` | n.a. | `ci.yml` release job |
| Every gate of the inventory stays wired into the commit suite or CI | [engineering](../openspec/specs/engineering/spec.md) | `QualityGatesTest` | gap: JAGT-96 | `QualityGatesTest` |
| One gate per stack; a check holds for all code; no hook in the repository | [AGENTS.md](../AGENTS.md#git-and-committing), 0162 | `./gradlew test --rerun-tasks` | gap: JAGT-96 | `jagt.conventions.gradle` |

## Review

| practice | rule home | Java | JS | copy |
|---|---|---|---|---|
| Review before every commit, a rejected finding keeps its why; a round is judgement, not a work order | [AGENTS.md](../AGENTS.md#the-human-in-the-loop), [review](../openspec/specs/review/spec.md) | a reader, no gate | same | the AGENTS.md lines |
| Mutants and rule tests over reading by eye | [testing](rules/testing.md) | `pitestDiff`, the rule tests | gap | as above |

## Docs and decisions

| practice | rule home | Java | JS | copy |
|---|---|---|---|---|
| Text budgets: decision 50, rule 100, page 200 lines; no line past 150 characters outside a table | [style](rules/style.md) | `TextBudgetTest` | n.a. | `TextBudgetTest`, `BudgetedTexts` |
| Every relative link and anchor reaches its target; a rule has its path-scoped pointer | [style](rules/style.md) | `DocLinksTest` | n.a. | `DocLinksTest`, `DocFiles` |
| An architectural decision has its record, indexed (25010 analysability) | [decisions](decisions/README.md) | `DecisionIndexTest` | n.a. | `DecisionIndexTest` |
| A behaviour change carries its spec delta; a capability its features line | [spec-changes](../openspec/specs/spec-changes/spec.md) | `SpecLimitsTest`, `FeaturesTest`, `OpenSpecTest` | n.a. | the three tests |
| A scenario names only a test that exists; a rule is reached from this page | [engineering](../openspec/specs/engineering/spec.md) | `EngineeringSpecTest`, `PracticesPageTest` | n.a. | the two tests |

## Agents and prompts

| practice | rule home | Java | JS | copy |
|---|---|---|---|---|
| A task opens only from a command or tracker intake, never a model's answer | [AGENTS.md](../AGENTS.md#the-human-in-the-loop) | `TaskOpeningDoorsTest` | n.a. | `TaskOpeningDoorsTest` |
| A prompt is budgeted and evaluated; a vendor CLI's screen is pinned on a capture | [style](rules/style.md), [seams](rules/seams.md) | `TextBudgetTest`, `promptEval`, `roleEval` | n.a. | `TextBudgetTest` |
| A guard names what it cannot stop; the base branch is read-only (ASVS V1 shell, 25010 integrity) | [guards](rules/guards.md), 0022, 0040 | `ToolGateTest`; arguments as lists, one fixed `sh -c` | n.a. | the guard tests |
| A new role starts from its design file | [components](rules/components.md) | `RoleModulesTest` | n.a. | `RoleModulesTest` |
| What the board draws has one meaning per colour and mark | [design](rules/design.md) | `BoardAssetsTest` | `boardTest` | `BoardAssetsTest` |

## Security

| practice | rule home | Java | JS | copy |
|---|---|---|---|---|
| A write carries its Origin; a remote peer authenticates (ASVS V4 CSRF, 25010 integrity) | [board-security](../openspec/specs/board-security/spec.md), 0167 | `BoardWriteFilterTest`, `OriginFilterTest`, `LoopbackFilterTest` | the board sends Origin | `BoardWriteFilterTest` |
| One role, one door; the agent door resolves its caller (ASVS V8) | [guards](rules/guards.md) | `AgentDoorFilterTest`, `McpProtocolServiceTest` | n.a. | `AgentDoorFilterTest` |
| Password is a BCrypt hash by env name; no default credential; an issuer subject is allow-listed (V6.2, V9/10, V11; 25010 authenticity) | [board-security](../openspec/specs/board-security/spec.md) | `AuthCheckTest`, `OidcLoginOnTomcatTest`, `MasterTokenTest` | n.a. | `AuthCheckTest` |
| Session fixation and rotation by the framework's defaults (V7) | 0167 | Spring Security, no own session code | n.a. | none |
| Logout ends the session; a guessing peer is backed off; every login is one structured line (V6.3, V7, V16; 25010 non-repudiation) | [board-security](../openspec/specs/board-security/spec.md) | `PasswordLoginRefusalOnTomcatTest`, `LoginBackoffFilterTest`, `PasswordLoginOutcomeTest` | the gear's log-out | the three tests |
| Content-Security-Policy of the board's own origin (V3.4) | [design](rules/design.md), 0184 | `BoardAssetsTest`, `BoardVendorLoadTest` | no CDN, no inline script | `BoardCsp` |
| Secure cookie, session timeout, forwarded headers, Ingress tls (V3.4, V7, V12) | [server-environment](../openspec/specs/server-environment/spec.md) | `ServerStarterTest`, `KubernetesManifestsTest` | n.a. | the two tests |
| Management exposes health only; the cookie is same-site strict (V13) | [server-environment](../openspec/specs/server-environment/spec.md), 0172 | `KubernetesManifestsTest` | n.a. | the manifest |
| No secret or personal value in a log (V14); a token file is rw owner only (25010 confidentiality) | [style](rules/style.md) | `TaskAliasLogFilterTest`, `MasterTokenTest` | n.a. | `TaskAliasLogFilterTest` |
| A file is served by a resource handler, never a request-driven path (V5; judged from the two handlers, a route reading a path reopens it) | [board-security](../openspec/specs/board-security/spec.md) | `BoardFolder`, `UserFiles` | n.a. | none |
| A threat model names assets, actors and the rows above (V15) | [threat-model](threat-model.md) | `ThreatModelTest`; guards have theirs in [guards](rules/guards.md) | n.a. | `ThreatModelTest` |
| HSTS (V3.4), MFA (V6.4), WebRTC (V17) | n.a. | TLS ends at the ingress; one owner, OIDC delegates MFA; unused | n.a. | none |

## Operations

| practice | rule home | Java | JS | copy |
|---|---|---|---|---|
| Probes: startup, liveness, readiness; graceful shutdown (Boot health groups, 12F IX; 25010 availability) | [shutdown](../openspec/specs/shutdown/spec.md), 0161, 0169, 0172, 0179 | `KubernetesManifestsTest`, `ShutdownEndingsTest`, `ClonesHealthTest`; tini and a TERM trap | n.a. | `KubernetesManifestsTest` |
| Failure is contained: breaker, wait-out, error shape (25010 fault tolerance) | [seams](rules/seams.md), 0086, 0105, 0108 | `AssistantBreakerTest`, `InfraRetryTest`, `RetryPolicyTest`, `AbortedConnectionFilterTest` | n.a. | `RetryPolicyTest` |
| State is written atomically and read across a minor (25010 recoverability, 12F XII) | [components](rules/components.md) | `AtomicFilesTest`, `StateServiceCompatibilityTest`, `TaskStateFileCompatibilityTest` | n.a. | `AtomicFilesTest` |
| Logs are structured to stdout with a cause (Boot logging, 12F XI) | [style](rules/style.md) | `LogbackTargetTest`, `FailureCausesTest` | n.a. | `FailureCausesTest` |
| Every store, call, loop and task count is bounded (25010 resource use, capacity) | [components](rules/components.md), 0045, 0048, 0104, 0179 | `ImageJvmOptionsTest`, `StandSlotsTest`, `LoopBreakerTest` | n.a. | `ImageJvmOptionsTest` |
| Install is one file and one command; two instances co-exist (25010 installability, co-existence, 12F VII) | [AGENTS.md](../AGENTS.md#interfaces), 0058 | `DockerComposeContainersTest`, `ServerContainerTest` | n.a. | `Dockerfile`, `kubernetes.yml.dist` |
| Configuration layered by env keys (Boot externalised, 12F III, I) | [components](rules/components.md), 0120, 0168 | `ConfigResolverTest` | n.a. | `ConfigResolverTest` |
| Metrics and build info answer to the owner (Boot metrics, info) | none yet | gap: JAGT-130 | gap: JAGT-130 | gap: JAGT-130 |
| Start-to-ready and latency stay under a measured budget (25010 time behaviour) | none yet | gap: JAGT-131 | gap: JAGT-131 | gap: JAGT-131 |
| Upgrade, backup and rollback of data are documented (25010 replaceability) | [server](server.md) | state fixture of one minor: gap: JAGT-132 | n.a. | gap: JAGT-132 |
| Scale out (12F VIII) and stateless processes (12F VI) | 0179 | rejected: one replica, state on `/data` | n.a. | none |

## Supply chain

| practice | rule home | Java | JS | copy |
|---|---|---|---|---|
| A bill of materials ships with a release (SBOM) | [release-artifacts](../openspec/specs/release-artifacts/spec.md), 0182 | `CiWorkflowTest`, `jagt.sbom.gradle` | gap | `jagt.sbom.gradle` |
| Signed provenance and checksums for the jar and the image (SLSA L2) | [release-artifacts](../openspec/specs/release-artifacts/spec.md), 0183 | `CiWorkflowTest` | n.a. | `ci.yml` release job |
| Dependencies, actions, base images and the wrapper are pinned; an update bot (12F II, SLSA integrity, update automation) | [engineering](../openspec/specs/engineering/spec.md), 0189 | `PinningTest`, lockfiles | n.a. | `PinningTest` |
| A dependency vulnerability stops the build (ASVS V13) | [engineering](../openspec/specs/engineering/spec.md), 0188 | osv-scanner on the release SBOM, `QualityGatesTest` | gap: JAGT-96 | `ci.yml` test job |
| One codebase, the image built from it (12F I) | 0171 | `Dockerfile` | n.a. | `Dockerfile` |
