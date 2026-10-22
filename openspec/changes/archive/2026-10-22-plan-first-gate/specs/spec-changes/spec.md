## MODIFIED Requirements

### Requirement: A hand-back carries a valid change
In a repository holding `openspec/`, a session's REVIEW_PENDING SHALL be refused until
`openspec/changes/<task>/` validates strict or says `skip_specs: true` (`HandBackDue`, `adapter/OpenSpec`).
The refusal is the validator's own words; the session fixes them and reports again. A plan is not gated:
it lists the change, which the session writes once the plan is approved.

#### Scenario: No change opened
- **WHEN** a session hands back ABC-42 in a repository holding `openspec/` with no `openspec/changes/abc-42/`
- **THEN** it is refused, told to run `openspec new change abc-42` or set `skip_specs: true`

#### Scenario: An invalid change
- **WHEN** `openspec/changes/abc-42/` holds no delta and no `skip_specs: true`
- **THEN** the hand-back is refused with what `openspec validate --strict` said

#### Scenario: A repository keeping no specs
- **WHEN** the repository holds no `openspec/`
- **THEN** nothing is owed
