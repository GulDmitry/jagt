## MODIFIED Requirements

### Requirement: The header says when the assistant is unavailable
While the headless breaker is open or probing, jagt's lost reach SHALL hold driver `assistant` on host `local`, saying
`assistant unavailable: ` and the provider's last error. It SHALL end when the breaker closes. No second mark exists.

#### Scenario: The breaker opens
- **WHEN** the breaker is open
- **THEN** the head's one reach mark shows `assistant unavailable: 529 Overloaded` and the tab icon is red
- **AND** it is not pressable

#### Scenario: The breaker closes
- **WHEN** a probe read succeeds
- **THEN** the mark is gone
