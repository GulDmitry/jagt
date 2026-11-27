## ADDED Requirements

### Requirement: A failed render is not an unreachable backend
The board SHALL toast a failed read of the state as an unreachable backend and a failed render as a render failure naming its cause.
#### Scenario: Rendering the state throws
- **WHEN** the state is read and painting it throws
- **THEN** the toast names a render failure and its cause, never an unreachable backend

#### Scenario: The state cannot be read
- **WHEN** the read of the state fails
- **THEN** the toast says the backend cannot be reached, with its cause
