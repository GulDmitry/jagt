## ADDED Requirements

### Requirement: A replayed QA read answers from its recording
With `orchestrator.agent.cli` set to `replay`, a QA read SHALL answer the recorded `QA_CHECK` read: its rows, its flow ids and its environment.
It SHALL answer failed only where the recording holds no QA read.

#### Scenario: A recorded read names a flow
- **WHEN** the recording holds a QA read naming flow `@AC-1` and a listed environment
- **THEN** the replayed read answers those rows, flow and environment, and jagt runs the flow

#### Scenario: A recording without a QA read
- **WHEN** the recording holds no QA read
- **THEN** the replayed read answers failed with "the recording holds no QA check"
