## ADDED Requirements

### Requirement: A prompt past a row's long-context threshold is charged the long-context rate
A turn whose prompt exceeds its row's long-context threshold SHALL be charged that row's long-context rates for every token kind.
The prompt is input, cache writes and cache reads together. Any other turn is charged the row's standard rates.

#### Scenario: A prompt just over the threshold
- **WHEN** a session log holds a turn of `claude-haiku-5-5` whose prompt is 100001 tokens
- **THEN** all its tokens are charged at the long-context rates

#### Scenario: A prompt at the threshold
- **WHEN** a session log holds a turn of `claude-haiku-5-5` whose prompt is 100000 tokens
- **THEN** all its tokens are charged at the standard rates
