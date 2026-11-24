## ADDED Requirements

### Requirement: A failing credential check holds a session's start
A project's `credentialChecks` SHALL each name a read-only argv command and what renews its credential.
jagt SHALL run each at start, daily, and before a session of that project starts, under a timeout, and read its exit code alone.
A failing check SHALL put one mark in the header naming the project, the check and what renews it; the next passing run ends it.
No session of that project SHALL start while one fails; its card says why in a note, never a problem, a badge or a stop.
#### Scenario: A registry token expired
- **WHEN** ABC-42's project declares check `npm registry` and it exits non-zero as ABC-42's session would start
- **THEN** no session opens, the card's note names the check and what renews it, and the header carries one mark
#### Scenario: The credential is renewed
- **WHEN** the check next exits zero, at the minutely read or as a session starts
- **THEN** the header mark and the card's note are gone, and the session opens
#### Scenario: A check prints a credential
- **WHEN** a check's output holds a token
- **THEN** jagt neither logs, shows nor stores that output, only the exit code
