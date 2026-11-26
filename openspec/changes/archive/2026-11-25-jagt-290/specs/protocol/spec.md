## ADDED Requirements

### Requirement: A blank field reads the same in every message
A message SHALL read a blank field as absent and strip a padded one, the same way for every message (`protocol/Wire`).
#### Scenario: A padded or blank field
- **WHEN** any message is built with a blank field, or a field padded with spaces
- **THEN** the blank one reads absent and the padded one is stripped

## MODIFIED Requirements

### Requirement: A hook's post is filtered, not refused
`protocol/SessionHookReport` SHALL filter what a hook posts, never refuse it: what jagt cannot believe is dropped, logged once.
#### Scenario: A relative log path
- **WHEN** a hook posts one
- **THEN** it is dropped, the rest kept
#### Scenario: A log path holding a NUL character
- **WHEN** a hook posts a path holding a NUL character
- **THEN** it is dropped as a violation, never thrown, the rest kept
