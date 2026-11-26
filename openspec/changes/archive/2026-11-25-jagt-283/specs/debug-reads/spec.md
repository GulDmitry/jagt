## ADDED Requirements

### Requirement: A debug read stays within its bounds
`read_record` SHALL read 1 to 1000 `lines` of a known record, 200 where `lines` is left out.
`read_transcript` SHALL keep 1 to 100000 `maxChars`, 16000 where left out.
A value outside, or an unknown record, SHALL be refused naming its field, and nothing is read.
#### Scenario: A read outside its bounds
- **WHEN** `read_record` asks for 0 or 1001 `lines`, or for a record that is none of the four, or `read_transcript` for 0 or 100001 `maxChars`
- **THEN** it is refused naming `lines`, `record` or `maxChars`
#### Scenario: A bound left out
- **WHEN** `read_record` leaves `lines` out, or `read_transcript` leaves `maxChars` out
- **THEN** it returns the last 200 lines, or the newest 16000 characters
