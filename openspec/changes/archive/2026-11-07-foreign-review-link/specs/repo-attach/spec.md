## ADDED Requirements

### Requirement: A review-request link is under its repository's own remote
A link SHALL be recorded for a repository only where its project path is under that repository's remote path.
A repository whose remote is unknown judges nothing.
A foreign link SHALL be dropped.
A ship whose every link is dropped SHALL be a missed one: no round is recorded and the status does not move.
A ship linking several repositories SHALL lose only its foreign links.
The board's head SHALL carry one line per dropped link, under the existing reach mark.
That line ends on the repository's next good link, or when the task closes.
The reporting session SHALL be told once, naming the expected remote path.
A foreign request found in `state.json` SHALL be cleared on the first read, with one INFO log line.
#### Scenario: The only link is foreign
- **WHEN** ABC-42 at `IN_PROGRESS` ships with a link outside its repository's remote
- **THEN** no round is recorded and the task stays `IN_PROGRESS`
- **AND** the board's head carries a line naming the expected remote path

#### Scenario: One of two links is foreign
- **WHEN** ABC-42 ships two repositories and one link is outside its remote
- **THEN** the good link is recorded as the round and the foreign one is not

#### Scenario: The session is told once
- **WHEN** a link is dropped
- **THEN** the reporting session is sent one line naming the expected remote path

#### Scenario: The line ends on a good link
- **WHEN** that repository's next report carries a link under its remote
- **THEN** its line leaves the board's head

#### Scenario: The line ends with the task
- **WHEN** the task closes while its line stands
- **THEN** the line leaves the board's head on the next read

#### Scenario: A foreign request already held
- **WHEN** `state.json` holds a request not under its repository's remote
- **THEN** the first read clears it and logs `foreign request cleared` once
