## MODIFIED Requirements

### Requirement: A task is one row
Each task SHALL be one row of at most two lines: alias, title, badge and the one verb the server marks `onRow`; then the status with its age
and the checks dot. A row whose poll stopped rings its status; in the band on top a row names its project where projects are several.
A flagged task's alias chip SHALL carry the card's filled flag mark.

#### Scenario: A title too long for its row
- **WHEN** a1's title is wider than its row
- **THEN** the row ends it in `…`, and hovering the title shows it whole; a title that fits shows no tip

#### Scenario: A poll stops on a closed task
- **WHEN** a1's poll stops while a2 is open
- **THEN** a1's row rings its status

#### Scenario: A task is flagged
- **WHEN** a task is flagged
- **THEN** its row leads its group
- **AND** its alias chip shows the filled flag mark; a cleared flag leaves the chip bare

#### Scenario: Focus from the list
- **WHEN** the human presses the row verb of a task that is not open
- **THEN** Focus runs on that task and the pane keeps the task it had
