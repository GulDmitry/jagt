## ADDED Requirements

### Requirement: The press log rolls at one megabyte
`interactions.jsonl` past 1 MB SHALL roll into one `interactions.jsonl.1`, replacing the last; the page holds at most
200 unsent presses.

#### Scenario: A long week of presses
- **WHEN** a batch takes the log past 1 MB
- **THEN** it becomes `interactions.jsonl.1`, the older `.1` is gone, and the next press starts a new file
