## ADDED Requirements

### Requirement: A local answer of low confidence goes to the cloud
Where `orchestrator.assistant.local.min-confidence` is above 0, a local read SHALL be asked of the cloud once when the
joint probability of the tokens spelling the values of the request schema's enum properties, from the response's
logprobs, is below it; free-text fields do not count. A response without logprobs, or a schema without an enum
property, counts as below. The default 0 accepts every answer; a value outside 0 to 1 is the default.

#### Scenario: The local model is unsure
- **WHEN** `min-confidence` is 0.8 and the local answer's enum-field confidence is 0.37
- **THEN** the cloud read answers the request and a WARN line names the confidence and the threshold

#### Scenario: The local model is sure of its choice and unsure of its words
- **WHEN** `min-confidence` is 0.8, the enum-field confidence is 0.9 and a free-text field's tokens are improbable
- **THEN** the local answer is the mapping

#### Scenario: The server sends no logprobs
- **WHEN** `min-confidence` is above 0 and the response carries no logprobs
- **THEN** the cloud read answers the request and a WARN line names the unknown confidence

#### Scenario: The schema has no enum property
- **WHEN** `min-confidence` is above 0 and the request schema has no property carrying `enum`
- **THEN** the cloud read answers the request and a WARN line names the unknown confidence

#### Scenario: No threshold
- **WHEN** `min-confidence` is 0
- **THEN** every local answer is accepted, whatever its confidence
