## ADDED Requirements

### Requirement: A local answer of low confidence goes to the cloud
Where `orchestrator.assistant.local.min-confidence` is above 0, a local read SHALL be asked of the cloud once when the
joint probability of the answer's tokens, from the response's logprobs, is below it. A response without logprobs
counts as below. The default 0 accepts every answer; a value outside 0 to 1 is the default.

#### Scenario: The local model is unsure
- **WHEN** `min-confidence` is 0.8 and the local answer's confidence is 0.37
- **THEN** the cloud read answers the request and a WARN line names the confidence and the threshold

#### Scenario: The local model is sure
- **WHEN** `min-confidence` is 0.8 and the local answer's confidence is 0.9
- **THEN** the local answer is the mapping

#### Scenario: The server sends no logprobs
- **WHEN** `min-confidence` is above 0 and the response carries no logprobs
- **THEN** the cloud read answers the request and a WARN line names the missing logprobs

#### Scenario: No threshold
- **WHEN** `min-confidence` is 0
- **THEN** every local answer is accepted, whatever its confidence
