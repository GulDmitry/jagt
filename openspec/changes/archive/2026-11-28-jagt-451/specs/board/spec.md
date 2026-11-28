## MODIFIED Requirements

### Requirement: Components ship as the files that run
The board SHALL render Preact components written with `htm`, served from `board/vendor/` byte for byte as the npm registry published them.
The page's import map resolves them. A page SHALL load nothing from outside the jar. The board's code and styles SHALL weigh under 224 KB,
the style book not counted (`BoardAssetsTest`). A manifest and two PNG icons from the jar make it installable from the URL the banner prints.
#### Scenario: A shipped file is not what ships
- **WHEN** `vendor/preact.mjs` differs from the published 11.2.0 by one byte
- **THEN** `BoardAssetsTest` fails naming the file
- **WHEN** a page under `board/` loads a script or a stylesheet from `https://`
- **THEN** `BoardAssetsTest` fails
- **WHEN** a human installs the board from Chrome or Safari
- **THEN** it opens in its own window at `/`, and `GET /manifest.webmanifest` answers `application/manifest+json`

### Requirement: The styleguide draws every state
`styleguide.html` SHALL render in both themes each component in each state its `styleguide/` fixtures name: a card per status
and stop trigger the backend sends, and the legend. A component reads no module state and no `ui/` module: its props carry all it shows.
#### Scenario: Opening the styleguide
- **WHEN** a human opens `/styleguide.html`; **THEN** each card state, status and stop trigger stands as a named card in both themes; nothing throws
