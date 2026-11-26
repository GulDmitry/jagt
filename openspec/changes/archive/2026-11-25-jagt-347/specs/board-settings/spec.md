## ADDED Requirements

### Requirement: A saved setting leaves the dialog as it was
The `jagt.yml` settings dialog SHALL keep its size and every field's position while a setting is saved: the restart footer holds its
height when empty. A row's hint always takes one line: `default` while `jagt.yml` leaves the key out, `set` once it has a value.
The row's hint repaints when a save succeeds.
An unset field shows the default the server sends as its placeholder.
#### Scenario: A restart-bound setting is saved
- **WHEN** the human changes a setting that needs a restart
- **THEN** the dialog's size and every field's position stay the same
- **AND** the row's hint reads `set`
