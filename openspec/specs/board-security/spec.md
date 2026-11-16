# board-security Specification

## Purpose
What the board does against a hostile page, a guessed password and an open session.

## Requirements

### Requirement: The board answers with a Content-Security-Policy of its own origin
Every response of the board SHALL carry a `Content-Security-Policy` that loads script, image, font, frame and connection from the
board's own origin only. A board page SHALL run no inline script but its import map, which the policy admits by hash; the vendored
editor and diagram libraries inject style, so `style-src` also admits inline style, and the favicon dot is a `data:` image.
#### Scenario: A page asks for a foreign script
- **WHEN** the board answers any request
- **THEN** its `Content-Security-Policy` names `'self'` for `default-src` and `script-src`, no `unsafe-eval` and no host

### Requirement: The owner logs out
`orchestrator.auth.mode` `password` or `oidc` SHALL end the session on `POST /logout`, which carries the page's Origin like every write,
and delete its cookie. The gear menu SHALL offer `log out` only on a board behind a login (`login` of `GET /api/session`); it replaces no mark.
#### Scenario: Logout
- **WHEN** the owner logs out
- **THEN** the session is invalidated and the next request lands on the login page
#### Scenario: A board with no login
- **WHEN** `auth` is `none`
- **THEN** the gear menu offers no `log out`
