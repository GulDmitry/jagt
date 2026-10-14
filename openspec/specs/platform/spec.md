# platform Specification

## Purpose

What jagt asks of the machine it runs on, each only when `jagt.yml` asks for it.

## Requirements

### Requirement: The machine stays awake only when asked
With `keepAwake: true`, the `keepawake` job SHALL hold `port/KeepAwake` while the Master is in `act`, auto-review
is enabled or `tracker.mode` takes work. Each minute it SHALL check, releasing the hold otherwise. On macOS it is `caffeinate -i -w
<jagt's pid>`, ending with jagt; elsewhere nothing. Unset, no job is declared and nothing is spawned.

#### Scenario: Unattended work on
- **WHEN** `keepAwake: true` and `master.mode: act`
- **THEN** the machine does not idle-sleep until the mode changes or jagt stops

#### Scenario: Not asked
- **WHEN** `keepAwake` is unset
- **THEN** nothing is spawned, whatever runs unattended
