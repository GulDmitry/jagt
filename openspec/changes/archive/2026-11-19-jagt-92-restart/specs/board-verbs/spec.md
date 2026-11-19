## MODIFIED Requirements

### Requirement: Upgrade is offered while a newer verified release is kept
`upgrade`, a `GlobalCommand` declaring itself `available`, SHALL be listed only while the release check holds a kept, verified release
newer than the running one. Pressed, it points `current` at it. Started by the launcher (`JAGT_LAUNCHER` set), jagt then stops gracefully with
exit code 75 and replies `restarting on <v>`; otherwise nothing exits and the reply is `restart to run <v>`.
A refused switch is shown named. It replaces nothing on a card: no task owns it. The Master never presses it.
#### Scenario: A newer release is kept
- **WHEN** the release check has kept and verified a newer release
- **THEN** the palette and `help` list `upgrade`; with none kept they do not, and typed it is refused
#### Scenario: Upgrade is pressed under the launcher
- **WHEN** the human runs `upgrade` for a kept `0.25.0` in a jagt the launcher started
- **THEN** `current` names `0.25.0`, the reply says `restarting on 0.25.0`, and jagt stops gracefully with exit code 75
- **AND** the launcher starts `0.25.0`
#### Scenario: Upgrade is pressed
- **WHEN** the human runs `upgrade` for a kept `0.25.0` in a jagt the launcher did not start
- **THEN** `current` names `0.25.0`, jagt keeps running, the reply says `restart to run 0.25.0` and `upgrade` is no longer listed
#### Scenario: The switch is refused
- **WHEN** the kept jar no longer matches its checksum
- **THEN** `current` is unchanged, jagt keeps running and the reply names the refusal, `MISMATCH`
