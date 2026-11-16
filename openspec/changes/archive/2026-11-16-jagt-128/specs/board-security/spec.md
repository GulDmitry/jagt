## ADDED Requirements

### Requirement: A peer that guesses the password is backed off
In `password` mode a peer address that failed the password `orchestrator.auth.backoff.failures` times (`5`) SHALL be refused.
Its `POST /login` answers 429 with `Retry-After` for `orchestrator.auth.backoff.duration` (`5m`), the right password included.
A success clears the peer's count; a failure older than the duration is forgotten.
The count is in memory and keeps `orchestrator.auth.backoff.peers` (`10000`) addresses at most, the least recently seen dropped.
#### Scenario: The limit is reached
- **WHEN** a peer fails the password 5 times
- **THEN** its next login is refused 429, whatever password it sends, until 5 minutes passed
#### Scenario: Another peer
- **WHEN** one peer is backed off
- **THEN** a login from any other address is judged on its password alone

### Requirement: Every login is one structured line
A login, `password` or `oidc`, SHALL write one line: `login succeeded` at INFO, `login failed` at WARN with its `cause`, each with `peer` and `mode`,
never the name or password sent. A backed-off attempt writes `login refused` with `peer` and `cause`.
#### Scenario: A wrong password
- **WHEN** a peer sends a wrong password
- **THEN** one `login failed` line names its address, `mode=password` and the cause
