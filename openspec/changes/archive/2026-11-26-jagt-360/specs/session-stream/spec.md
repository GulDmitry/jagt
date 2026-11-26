## ADDED Requirements

### Requirement: A herdr window streams as a redrawn screen
With `orchestrator.session-host: herdr` (`HerdrWindowStream`) the Session tab SHALL show the window by redrawing its screen
whenever it differs from the last one sent, because herdr streams no output. The tab's size SHALL NOT change the window:
it keeps the size herdr's own client gives it.
#### Scenario: A herdr session's tab
- **WHEN** the human opens the Session tab of a session held by herdr
- **THEN** it shows the screen and redraws it when the screen changes
#### Scenario: Resizing the tab
- **WHEN** the human resizes the tab over a herdr window
- **THEN** the window keeps its size
