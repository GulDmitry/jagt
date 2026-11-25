## ADDED Requirements

### Requirement: An unreadable jar is not a rebuilt one
A running jar that cannot be read, other than by its absence, SHALL raise no rebuilt banner. The read is logged and tried again.
#### Scenario: A stamp read fails
- **WHEN** the running jar's stamp read fails with an error other than the file being gone
- **THEN** no banner shows and the watch tries again next minute
