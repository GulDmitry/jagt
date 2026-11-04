## ADDED Requirements

### Requirement: A read that needs no tool may ask a local model
Where `orchestrator.assistant.local.endpoint` is set, a read of a kind in `orchestrator.assistant.local.kinds`
(`COMMAND_MAP` unless set) SHALL go to that OpenAI-compatible endpoint.
The read's schema is sent as its response format.
A local read that fails, times out or answers outside the schema SHALL be asked of the cloud once, and a WARN says why.
With no endpoint set every read goes to the cloud.
The start SHALL be refused, naming the key, in three cases: an endpoint that is no http(s) address,
a blank `model`, a listed kind that reads tools.

#### Scenario: A local model is configured
- **WHEN** an endpoint is set and the operator types a request jagt maps onto a command
- **THEN** the read goes to the endpoint and its answer is the mapping

#### Scenario: The endpoint fails or is too slow
- **WHEN** the endpoint refuses the connection, answers with an error or outlasts `timeout`
- **THEN** the cloud read answers the request and a WARN line names the cause

#### Scenario: A kind that is not listed
- **WHEN** a read of a kind outside `kinds` is asked
- **THEN** it goes to the cloud

#### Scenario: A misconfigured local model
- **WHEN** the endpoint is set and `model` is blank, the endpoint is not http(s), or `kinds` lists `TICKET_READ`
- **THEN** the start is refused naming `orchestrator.assistant.local.model`, `.endpoint` or `.kinds`
