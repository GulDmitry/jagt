# spec-changes Specification

## Purpose
The change a task adds to the behaviour its repository records as OpenSpec specs, gated and folded by jagt itself.

## Requirements

### Requirement: A hand-back carries a valid change
In a repository holding `openspec/`, a session's REVIEW_PENDING SHALL be refused until
`openspec/changes/<task>/` validates strict or says `skip_specs: true` (`HandBackDue`, `adapter/OpenSpec`).
The refusal is the validator's own words; the session fixes them and reports again. A plan is not gated:
plan mode cannot write the change.

#### Scenario: No change opened
- **WHEN** a session hands back ABC-42 in a repository holding `openspec/` with no `openspec/changes/abc-42/`
- **THEN** it is refused, told to run `openspec new change abc-42` or set `skip_specs: true`

#### Scenario: An invalid change
- **WHEN** `openspec/changes/abc-42/` holds no delta and no `skip_specs: true`
- **THEN** the hand-back is refused with what `openspec validate --strict` said

#### Scenario: A repository keeping no specs
- **WHEN** the repository holds no `openspec/`
- **THEN** nothing is owed

### Requirement: A ship folds the change into the specs
`ship` SHALL validate and archive every open change of the task (`<task>` and `<task>-<n>`) in each of its
repositories. It does so before the session is told to commit (`SpecFold`). The delta so reaches the request beside the code.
A change that does not validate refuses the ship, and the task does not move.

#### Scenario: A valid change
- **WHEN** you `ship ABC-42` with a valid `openspec/changes/abc-42/`
- **THEN** it moves to `openspec/changes/archive/<date>-abc-42/` and the main specs carry its delta in the commit

#### Scenario: A later round
- **WHEN** a round after the first ship changes behaviour
- **THEN** the session opens `abc-42-2`, since `abc-42` is archived; an archived change owes nothing more

### Requirement: The CLI reports nothing home
jagt SHALL run the `openspec` CLI with `OPENSPEC_TELEMETRY=0`; a repository holding `openspec/` on a machine without
the CLI is refused naming the install, which is the human's to fix.

#### Scenario: CLI missing
- **WHEN** the CLI is not installed and the repository holds `openspec/`
- **THEN** the hand-back is refused naming `npm i -g @fission-ai/openspec`
