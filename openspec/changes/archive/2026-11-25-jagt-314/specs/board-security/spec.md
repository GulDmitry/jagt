## MODIFIED Requirements

### Requirement: The owner logs out
`orchestrator.auth.mode` `password` or `oidc` SHALL end the session on `POST /logout`, which carries the page's Origin like every write,
and delete its cookie. The gear menu SHALL offer `log out` only on a board behind a login (`login` of `GET /api/session`); it replaces no mark.
A login SHALL return only to a page the logged-out browser asked for: a `GET` outside `/api/**` not asking for JSON; else it lands on `/`.
#### Scenario: Logout
- **WHEN** the owner logs out
- **THEN** the session is invalidated and the next request lands on the login page
#### Scenario: A board with no login
- **WHEN** `auth` is `none`
- **THEN** the gear menu offers no `log out`
#### Scenario: A refused call before the login
- **WHEN** a logged-out board's `POST /api/interactions` is refused and the owner logs in
- **THEN** the browser lands on `/`, not on `/api/interactions`
