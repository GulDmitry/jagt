## ADDED Requirements

### Requirement: Upgrade is offered while a newer verified release is kept
`upgrade`, a `GlobalCommand` declaring itself `available`, SHALL be listed only while the release check holds a kept, verified release
newer than the running one. Pressed, it points `current` at it and replies `restart to run <v>`, restarting nothing.
A refused switch is shown named. It replaces nothing on a card: no task owns it. The Master never presses it.
#### Scenario: A newer release is kept
- **WHEN** the release check has kept and verified a newer release
- **THEN** the palette and `help` list `upgrade`; with none kept they do not, and typed it is refused
#### Scenario: Upgrade is pressed
- **WHEN** the human runs `upgrade` for a kept `0.25.0`
- **THEN** `current` names `0.25.0`, the reply says `restart to run 0.25.0` and `upgrade` is no longer listed
#### Scenario: The switch is refused
- **WHEN** the kept jar no longer matches its checksum
- **THEN** `current` is unchanged and the reply names the refusal, `MISMATCH`
