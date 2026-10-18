## ADDED Requirements

### Requirement: Components ship as the files that run
The board SHALL render Preact components written with `htm`, served from `static/vendor/` byte for byte as the npm
registry published them and resolved by the page's import map. A page SHALL load nothing from outside the jar, and
the board's code and styles SHALL weigh under 160 KB (`BoardAssetsTest`).

#### Scenario: A vendored file is edited
- **WHEN** `vendor/preact.mjs` differs from the published 11.0.1 by one byte
- **THEN** `BoardAssetsTest` fails naming the file

#### Scenario: A page reaches for a CDN
- **WHEN** a page under `static/` loads a script or a stylesheet from `https://`
- **THEN** `BoardAssetsTest` fails

### Requirement: The styleguide draws every state
`styleguide.html` SHALL render each component in each state its `styleguide/` fixtures name, with the modules the
board renders. A component reads no module state: everything it shows arrives in its props.

#### Scenario: Opening the styleguide
- **WHEN** a human opens `/styleguide.html`
- **THEN** every card state stands as one card under its name, and the page throws nothing

### Requirement: A press is logged as a control and a status
The board SHALL log each press as `{at, control, status}` to `interactions.jsonl` beside `state.json`, unless
`interactionLog: false`. `control` names the verb, report or element; `status` is the task's status or null. A press
SHALL NOT carry a task id, an alias, a ticket or typed text. A batch is sent two seconds after a burst begins, holds
at most 50 presses, and is refused whole when one press breaks the shape.

#### Scenario: Pressing Focus
- **WHEN** the human presses Focus on an agent's working task
- **THEN** a line holds `action:focus` and `IN_PROGRESS`, and nothing naming the task

#### Scenario: A malformed press
- **WHEN** a batch carries a control with a space, a status in lower case, or a date a day away
- **THEN** it is refused with a sentence, and nothing of it is written

#### Scenario: Switched off
- **WHEN** `interactionLog: false`
- **THEN** a batch is checked and nothing is written
