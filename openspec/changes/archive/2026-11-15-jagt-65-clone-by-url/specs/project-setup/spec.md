## ADDED Requirements

### Requirement: A project named by url is cloned
A project naming `url` SHALL use its `path` as is when that folder exists, and nothing is cloned.
With no `path`, or a missing one, jagt SHALL clone the url into `repos/<project>` under the data folder. It does so at start, which waits for it.
The git CLI's own credentials serve. A clone that fails SHALL be ONE mark on the board's head naming the project and the cause, never a card.
The other projects work, the clone is tried again each minute, and the mark ends with the clone.
#### Scenario: A url with no path
- **WHEN** a project names a url and no path
- **THEN** jagt clones it into `repos/<project>` at start and a task starts on it
#### Scenario: A path that exists
- **WHEN** a project names a url and a path that exists
- **THEN** that path is used and nothing is cloned
#### Scenario: A url that cannot be cloned
- **WHEN** one project's clone fails and another project names a repository that exists
- **THEN** the board's head names the failing project and the cause, and a task starts on the other project
- **AND** the mark ends when a later try clones it
