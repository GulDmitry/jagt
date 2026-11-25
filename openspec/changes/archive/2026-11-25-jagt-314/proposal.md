## Why

A logged-out board's keepalive `POST /api/interactions` was saved as the page to return to, so the next login landed on
`GET /api/interactions` and showed a JSON error.

## What Changes

- Only a page navigation is saved for after login: a `GET` outside `/api/**` not asking for JSON; a login otherwise lands on `/`.

## Capabilities

### New Capabilities

### Modified Capabilities

- `board-security`: a login returns only to a page.
