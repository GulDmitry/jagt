## MODIFIED Requirements

### Requirement: Rows keep their place
Rows SHALL keep their order inside their group: the tasks waiting on the human first, then each project by
name; only `order: alias` lets a new task take a retired one's place. The filter box (`/`)
matches alias, ticket number or title; `Esc` clears.

#### Scenario: A task turns to the human
- **WHEN** a task's checks fail while another task's agent works
- **THEN** its row moves into the band on top, and the other row keeps its place

#### Scenario: Projects by name
- **WHEN** `jagt.yml` lists `beta` before `alpha`
- **THEN** the task list and the launch boxes show `alpha` first; ticking both still sends `beta,alpha`

