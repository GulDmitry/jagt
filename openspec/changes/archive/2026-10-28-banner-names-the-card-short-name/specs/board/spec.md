## MODIFIED Requirements

### Requirement: A banner leads to its task
A desktop banner SHALL click through to its task (`UserNotifier.notify(…, link)`), macOS-only: no caller may depend
on it.

#### Scenario: Clicking a notification
- **WHEN** the human clicks one
- **THEN** the board opens filtered to that task; elsewhere the title names the task
- **WHEN** `ABC-42`, shown on its card as `p3`, stops
- **THEN** the banner reads `jagt · p3 · ABC-42 · agent stopped`; a task without a short name reads `jagt · ABC-42 · agent stopped`
