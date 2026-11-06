## MODIFIED Requirements

### Requirement: The machine stays awake only when asked
With `keepAwake: true`, the `keepawake` job SHALL hold `port/KeepAwake` while the Master is in `act`, auto-review
is enabled or `tracker.mode` takes work. Each minute it SHALL check, releasing the hold otherwise. On macOS it is `caffeinate -i -w
<jagt's pid>`, on Linux `systemd-inhibit --what=idle:sleep` around `tail --pid=<jagt's pid>`, each ending with jagt.
Unset, no job is declared and nothing is spawned.

#### Scenario: Unattended work on
- **WHEN** `keepAwake: true` and `roles.master.mode: act`
- **THEN** the machine does not idle-sleep until the mode changes or jagt stops

#### Scenario: Not asked
- **WHEN** `keepAwake` is unset
- **THEN** nothing is spawned, whatever runs unattended
