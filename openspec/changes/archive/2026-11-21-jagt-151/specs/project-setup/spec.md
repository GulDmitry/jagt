## ADDED Requirements

### Requirement: A number outside its range is refused
A start SHALL refuse a numeric key of `jagt.yml` that is zero, negative or past its ceiling, naming the key, the value and the allowed range; an unset key keeps its default (`ConfigCheck`).
#### Scenario: A value outside the range
- **WHEN** `loopBreaker.rounds` is `-1` or `999` and you start jagt
- **THEN** it is refused naming `orchestrator.loopBreaker.rounds`, the value and `1 to 19`
#### Scenario: An unset key
- **WHEN** `loopBreaker.rounds` is not written
- **THEN** its default applies and the start goes on
