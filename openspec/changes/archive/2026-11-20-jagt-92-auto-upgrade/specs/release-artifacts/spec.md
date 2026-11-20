## ADDED Requirements

### Requirement: A kept release is switched on its own only when the key is on
`orchestrator.releaseFeed.autoUpgrade` SHALL default to false.
When true, after each release check jagt SHALL switch to the kept release as `upgrade` does and exit with code 75.
It SHALL do so only in a jagt the launcher started (`JAGT_LAUNCHER` set).
It SHALL do so only while no task has an agent session up (working, idle or quiet).
A refused switch SHALL leave the release kept and SHALL NOT be tried again for that version; another kept version is tried.
#### Scenario: Nothing runs
- **WHEN** the key is true, jagt runs under the launcher, a verified `0.25.0` is kept and no agent session is up
- **THEN** `current` names `0.25.0` and jagt exits with code 75
#### Scenario: A session runs
- **WHEN** the key is true and a task has an agent session up
- **THEN** `current` is unchanged, the release stays kept and the next release check tries again
#### Scenario: The key is off
- **WHEN** the key is false or absent and a verified release is kept
- **THEN** `current` is unchanged
#### Scenario: Not under the launcher
- **WHEN** the key is true and the launcher did not start jagt
- **THEN** `current` is unchanged, because nothing would start jagt again
#### Scenario: The switch is refused
- **WHEN** the key is true and the kept jar no longer matches its checksum
- **THEN** `current` is unchanged, the release stays kept, the board's `upgrade` names the refusal and later checks do not retry that version
