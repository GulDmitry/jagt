## MODIFIED Requirements

### Requirement: A number outside its range is refused
A start SHALL refuse a numeric key that is zero, negative, past its ceiling or below its floor, naming key, value and range.
A live read SHALL keep the key's last valid value, its default before any.
It SHALL mark the board's head naming key, value and range until a read finds the key valid or unwritten.
Floors: `autoReview.maxIntervalMinutes` the min interval, `autoReview.waitingMaxIntervalMinutes` the max.
An unset key keeps its default (`ConfigRanges`).
#### Scenario: A value outside the range
- **WHEN** `loopBreaker.rounds` is `-1` or `999` and you start jagt
- **THEN** it is refused naming `orchestrator.loopBreaker.rounds`, the value and `1 to 19`
#### Scenario: An unset key
- **WHEN** `loopBreaker.rounds` is not written
- **THEN** its default applies and the start goes on
#### Scenario: A live edit outside the range
- **WHEN** `loopBreaker.rounds` was read as `7` and `jagt.yml` is edited to `-1`
- **THEN** the read says `7` and the board's head names the key, `-1` and `1 to 19`
#### Scenario: A live edit corrected
- **WHEN** the edit is corrected to `5`
- **THEN** the next read says `5` and the mark is gone
#### Scenario: A max interval below its floor
- **WHEN** `autoReview.maxIntervalMinutes` is `9` and `autoReview.minIntervalMinutes` is `10`
- **THEN** it is refused naming the key, `9` and `at least 10`
