# platform Specification

## Purpose

What jagt asks of the machine it runs on, each only when `jagt.yml` asks for it, and what it never takes from the machine: its locale and zone.

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

### Requirement: jagt shows every time in UTC and every text in English
`main` SHALL set the default locale to English and the default zone to UTC before anything else. The board SHALL format
every time in UTC and English, and mark UTC where a time is shown in full. Nothing jagt shows or writes SHALL depend on
the machine's or the browser's locale or zone.

#### Scenario: A board in Berlin
- **WHEN** a task changed at 14:05 UTC and the board is open in a browser set to Berlin
- **THEN** its history reads 14:05

#### Scenario: A machine in Turkish
- **WHEN** jagt starts on a machine whose language is Turkish
- **THEN** every case change and every date it renders is the English one
