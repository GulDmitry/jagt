## ADDED Requirements

### Requirement: Headless reads stop after repeated provider failures
After `orchestrator.assistant.breakerFailures` (3) consecutive model-provider failures, jagt SHALL refuse every
headless read for `orchestrator.assistant.breakerCooldown` (5m), then let one probe through. A refused read SHALL fail
as `AssistantUnavailable` naming the last provider error, never queued, never as a missing item.

#### Scenario: The provider keeps failing
- **WHEN** three headless reads in a row end on `API Error: 529 Overloaded`
- **THEN** the next read is refused as unavailable naming `529 Overloaded`, and no process starts
- **AND** opening is logged with its cause

#### Scenario: The cool-down has passed
- **WHEN** the cool-down has passed and a read arrives
- **THEN** that read alone runs; its success closes the breaker, its failure refuses reads for another cool-down
- **AND** a second read arriving during the probe is refused
- **AND** probing, re-opening and closing are each logged with their cause

#### Scenario: What counts as a provider failure
- **WHEN** a run ends on a 5xx, an overloaded or a network error
- **THEN** it counts; a usage limit, a timeout, a spend ceiling or an unusable answer does not
- **AND** a usage limit leaves the session wake path untouched

### Requirement: The header says when the assistant is unavailable
While the headless breaker is open or probing, the header SHALL show a `.chip.bad` reading `assistant unavailable`.
Its hover names the provider's last error and the time to the next probe.

#### Scenario: The breaker opens
- **WHEN** the breaker is open
- **THEN** the chip shows and the tab icon is red
- **AND** the chip reuses the mark of `backend unreachable` and a failed job, in that legend row
- **AND** it is not pressable

#### Scenario: The breaker closes
- **WHEN** a probe read succeeds
- **THEN** the chip is gone
