## MODIFIED Requirements

### Requirement: Every enabled model role's log opens in the board's pane
Each model role that runs SHALL have one chip in the head, named by its id: the Master, acceptance, triage and any added later.
Each has one feed, an in-memory logback appender `ROLE_<id>` on its module's classes, read at `/api/roles/<id>/log`, announced by `role-log`.
A disabled role, or one a human holds, has no chip. jagt opens no tmux window for a role.
#### Scenario: A role's log
- **WHEN** the human presses the Master's chip
- **THEN** its log stands in the pane, newest entry on top, and follows new entries
- **WHEN** the human presses the chip of the open log
- **THEN** the pane hides it and the Master keeps working
#### Scenario: A role that does not run
- **WHEN** a role is disabled, or a human holds it
- **THEN** the head shows no chip for it

### Requirement: Components ship as the files that run
The board SHALL render Preact components written with `htm`, served from `board/vendor/` byte for byte as the npm registry published them. The page's
import map resolves them. A page SHALL load nothing from outside the jar, and the board's code and styles SHALL weigh under 236 KB
(`BoardAssetsTest`). Its `manifest.webmanifest` and two PNG icons, served from the jar, make it installable as an app from the URL the banner prints.
#### Scenario: A shipped file is not what ships
- **WHEN** `vendor/preact.mjs` differs from the published 11.2.0 by one byte
- **THEN** `BoardAssetsTest` fails naming the file
- **WHEN** a page under `board/` loads a script or a stylesheet from `https://`
- **THEN** `BoardAssetsTest` fails
- **WHEN** a human installs the board from Chrome or Safari
- **THEN** it opens in its own window at `/`, and `GET /manifest.webmanifest` answers `application/manifest+json`

## ADDED Requirements

### Requirement: Every log view lists newest first
The Master's and every role's log, the session log and the `tail` report SHALL render through one `LogLines` component.
One block per entry, newest on top, a 6px gap; time (`HH:MM:SS` UTC) and INFO level `--muted`, WARN and ERROR `--danger`, message `--text`.
The legend has a row for the muted time and one for a danger entry. The session tab's `.screen` stays in terminal order.
#### Scenario: A log
- **WHEN** the human opens the Master's log holding a refusal, a move and a note
- **THEN** the newest entry is on top, a visible gap separates entries, the refusal is `--danger` and each time is muted
- **WHEN** entries arrive while the human has scrolled down
- **THEN** the entries being read stay in view
