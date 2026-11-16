# board-app Specification

## Purpose
The board as an app of its own: the backend serves its files as opaque assets,
coupled only through `contracts/`, and the board owns its language catalogs.

## Requirements

### Requirement: The board is its own app
The backend SHALL name no board file: the jar carries a copy of `board/` and serves it as opaque files. The only coupling is `contracts/`:
`openapi.yaml` holds every `/api` route (the agent's and `/mcp` excluded) and the login's public paths, `board-events.schema.json` the event
stream (`WholeApplicationWiringTest`). `orchestrator.board.dir` SHALL serve a folder in the jar's place, a relative one against the root, so an edit
shows on reload.
#### Scenario: A route the contract lacks
- **WHEN** a controller maps a route under `/api` that `contracts/openapi.yaml` lacks
- **THEN** `WholeApplicationWiringTest` fails naming the route
#### Scenario: A folder served
- **WHEN** `board.dir` is `my-board` and the root holds `my-board/probe.html`
- **THEN** `GET /probe.html` answers that file, and an edit shows on the next request

### Requirement: The board owns its catalogs
The catalogs are the board's own, in `board/i18n/`. The backend SHALL serve `<root>/i18n/` as plain files; a catalog there overrides the
shipped one of the same code, and a language only the root has is listed in `<root>/i18n/index.json`.
#### Scenario: A catalog in the root
- **WHEN** `<root>/i18n/pl.json` holds a key and the board loads
- **THEN** Polish is offered with that key overriding the shipped one
