## MODIFIED Requirements

### Requirement: A restart asks for the report it may have lost
Once per start, jagt SHALL say the `restarted` step to each task whose turn is the session's and open
(`RestartAskJob`). It sends through the relay that records the turn first. It asks only a session whose log was
written after jagt's last alive stamp (`AliveStamp`, rewritten every minute); with no readable stamp it asks none.
A session not running, or with the human's draft in its window, SHALL be asked nothing and not revived for it.
Its turn stays open as it was.

#### Scenario: A session holds the turn across a restart
- **WHEN** jagt starts and ABC-42's running session holds an open turn and wrote its log while jagt was down
- **THEN** the session is told "jagt was restarted" and to repeat its last status report, once
- **AND** any status report is taken as its reply

#### Scenario: Nobody holds a session turn, or it lost nothing
- **WHEN** jagt starts and ABC-43's turn is not an open session turn, or its log is older than the alive stamp
- **THEN** its session is told nothing

#### Scenario: The session is not running
- **WHEN** jagt starts and ABC-44 holds an open session turn but its session is down
- **THEN** nothing is sent and no session is started for it
