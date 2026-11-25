## MODIFIED Requirements

### Requirement: The owner logs out
`orchestrator.auth.mode` `password` or `oidc` SHALL end the session on `POST /logout`, which carries the page's Origin like every write,
and delete its cookie. The gear menu SHALL offer `log out` only on a board behind a login (`login` of `GET /api/session`); it replaces no mark.
#### Scenario: Logout
- **WHEN** the owner logs out
- **THEN** the session is invalidated and the next request lands on the login page
#### Scenario: A board with no login
- **WHEN** `auth` is `none`
- **THEN** the gear menu offers no `log out`

## ADDED Requirements

### Requirement: A login returns only to a page
A login SHALL return only to a page the logged-out browser navigated to: a `GET` outside `/api/**` with `Sec-Fetch-Mode: navigate` or
`Sec-Fetch-Dest: document`, or, without those headers, an `Accept` preferring `text/html`; else it lands on `/`.
#### Scenario: A static asset before the login
- **WHEN** a logged-out browser's `GET /favicon.ico` with `Accept: image/*` is refused and the owner logs in
- **THEN** the browser lands on `/`, not on `/favicon.ico`
#### Scenario: A refused call before the login
- **WHEN** a logged-out board's `POST /api/interactions` is refused and the owner logs in
- **THEN** the browser lands on `/`, not on `/api/interactions`
