## RENAMED Requirements

- FROM: `### Requirement: Detection is deterministic and the one host write is a re-run`
- TO: `### Requirement: Detection is deterministic and a host write is named`

## MODIFIED Requirements

### Requirement: Detection is deterministic and a host write is named
Detection SHALL be deterministic (a cadence, a status, an open request), a new trigger's too.
A host write is a re-run after an infrastructure death ([infra-wait](../infra-wait/spec.md)) or a note ([code-review](../code-review/spec.md)).

#### Scenario: A new trigger
- **WHEN** a trigger is added to the poll
- **THEN** it detects by a cadence, a status or an open request, never by a model's word
