## ADDED Requirements

### Requirement: A compacted session keeps its task facts
The brief a session is answered after a compaction SHALL name the task key, which is also its branch, the title,
the status on the board, and the last report: the open question, else the status message.

#### Scenario: A session was last reported as working
- **WHEN** a compacted session's task holds the message `outcome=progress — v3 routes done`
- **THEN** the brief names the task as its branch and `your last report: outcome=progress — v3 routes done`
