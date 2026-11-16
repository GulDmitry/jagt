## Why
A logged-in board had no way to end its session: a shared browser stayed signed in until the cookie expired.

## What Changes
- `POST /logout` invalidates the session and deletes its cookie.
- The gear menu offers `log out` when the board sits behind a login; `GET /api/session` says so with `login`.
