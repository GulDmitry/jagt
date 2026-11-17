## MODIFIED Requirements

### Requirement: Every window jagt opens is on the Session tab
A window jagt opens SHALL show on the Session tab and take `say`, whether a task's session or not: a login and the
acceptance scenarios session. The tab lists the open task's session first, then every such window by name.
#### Scenario: A window that is no task's
- **WHEN** jagt opens a login or the scenarios window
- **THEN** the Session tab lists it, streams its terminal live, and a line typed under it reaches that window
