## MODIFIED Requirements

### Requirement: Components ship as the files that run
The board SHALL render Preact components written with `htm`, served from `static/vendor/` byte for byte as the npm
registry published them. The page's import map resolves them. A page SHALL load nothing from outside the jar, and
the board's code and styles SHALL weigh under 160 KB (`BoardAssetsTest`). Its `manifest.webmanifest` and two PNG icons,
served from the jar, make it installable as an app from the URL the banner prints.

#### Scenario: A shipped file is not what ships
- **WHEN** `vendor/preact.mjs` differs from the published 11.0.1 by one byte
- **THEN** `BoardAssetsTest` fails naming the file
- **WHEN** a page under `static/` loads a script or a stylesheet from `https://`
- **THEN** `BoardAssetsTest` fails
- **WHEN** a human installs the board from Chrome or Safari
- **THEN** it opens in its own window at `/`, and `GET /manifest.webmanifest` answers `application/manifest+json`
