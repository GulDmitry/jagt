## MODIFIED Requirements

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
- **WHEN** the board's JS changes **THEN** its `npm test` is a no-op: gap, JAGT-96
