## ADDED Requirements

### Requirement: A restart asks for the report it may have lost
Once per start, after the application is ready, jagt SHALL say the `restarted` step to each task whose turn is the
session's and open. It does so through the relay that records the turn before it sends (`RestartAskJob`).
A task holding no open session turn SHALL be asked nothing.
A session not running, or with the human's draft in its window, SHALL be asked nothing and not revived for it.
Its turn stays open as it was.

#### Scenario: A session holds the turn across a restart
- **WHEN** jagt starts and ABC-42's running session holds an open turn
- **THEN** the session is told "jagt was restarted" and to repeat its last status report, once
- **AND** any status report is taken as its reply

#### Scenario: Nobody holds a session turn
- **WHEN** jagt starts and ABC-43's turn is the human's, the Master's or closed
- **THEN** its session is told nothing

#### Scenario: The session is not running
- **WHEN** jagt starts and ABC-44 holds an open session turn but its session is down
- **THEN** nothing is sent and no session is started for it
