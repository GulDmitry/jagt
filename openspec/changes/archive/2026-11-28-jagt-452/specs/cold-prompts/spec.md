## ADDED Requirements

### Requirement: A silent session is woken before the human is told
A session whose turn ended with no report and whose process runs SHALL show nothing new on the card and ping nobody; `SilentWake` says the `silent`
step to it once, hands the task to a fresh session once, each given `watchdog.staleAfter`, then stops it as `SILENT`: a jagt stop (`GUARD`) ending on
the session's report, the Master's first in `act`. Any report ends the wake. A session whose process does not run is silent at once.
#### Scenario: The nudge and the relay bring nothing
- **WHEN** ABC-42's session ended its turn with no report and its process still runs
- **THEN** the card shows nothing new and jagt tells the session `silent` once
- **WHEN** `watchdog.staleAfter` passes with no report
- **THEN** jagt hands the task to a fresh session once, still showing nothing new
- **WHEN** `watchdog.staleAfter` passes again with no report
- **THEN** the task stops as `SILENT`: a jagt stop naming that its end is the session's report, never the problem colour
#### Scenario: The session answers
- **WHEN** the nudge or the fresh session reports
- **THEN** nothing is shown and no stop is set
#### Scenario: The human is typing in its window
- **WHEN** a draft sits unsent in ABC-42's window
- **THEN** nothing is typed over it
