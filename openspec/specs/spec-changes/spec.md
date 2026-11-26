# spec-changes Specification

## Purpose
The change a task adds to the behaviour its repository records as OpenSpec specs, gated and folded by jagt itself.

## Requirements

### Requirement: A hand-back carries a valid change
In a repository holding `openspec/`, a session's REVIEW_PENDING SHALL be refused until
`openspec/changes/<task>/` validates strict or says `skip_specs: true` (`HandBackDue`, `adapter/worktree/OpenSpec`).
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

#### Scenario: A delta archive cannot apply
- **WHEN** a change validates but names a requirement archive cannot apply (MODIFIED of a missing one, ADDED of an existing one)
- **THEN** the hand-back is refused with what the validator said

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

### Requirement: A project names where its specs live
A project's `specs.path` SHALL name its spec root inside a repository, and `specs.project` the project whose repository
holds it; unset, a repository's own `openspec/` is read (`TaskSpecs`). The gate and the fold SHALL read that root in the
task's worktree of the project named. A task holding none is refused, told to ask for that project with `needsProject`.
A change SHALL be the task's whatever the case of its name, its slug included.

#### Scenario: Specs in another repository
- **WHEN** `api` names `specs/openspec` in `docs`, and ABC-42 holds a worktree of both with `ABC-42-login` open there
- **THEN** the hand-back gate validates `specs/openspec/changes/ABC-42-login` in the `docs` worktree

#### Scenario: The spec repository is not on the task
- **WHEN** `api` names its specs in `docs`, and ABC-42 holds no worktree of `docs`
- **THEN** the hand-back and the ship are refused, naming `needsProject: docs`

#### Scenario: A root the CLI cannot read
- **WHEN** a project's spec root is not a folder named `openspec`
- **THEN** the hand-back is refused saying so

### Requirement: A fixed bug names its case
A fixed bug SHALL be written as one WHEN/THEN from the real timeline, then matched against the capability's scenarios by WHEN and state.
Its commit says `Case: existing <scenario>`, `Case: new <scenario>` or `Case: none`.
#### Scenario: A THEN that is itself wrong
- **WHEN** the code follows a scenario whose THEN is wrong
- **THEN** the spec changes first, and an owner question is asked if it contradicts the code
#### Scenario: A fix restores a stated scenario
- **WHEN** a defect breaks what a scenario's WHEN and THEN already say
- **THEN** the commit names that scenario as `Case: existing` and fixes the test that missed it
#### Scenario: A fix for a state the spec does not name
- **WHEN** the trigger has a scenario but the state or timing of the defect is not in it
- **THEN** the change adds a scenario for it before the code, and the commit says `Case: new`
#### Scenario: A fix to the build
- **WHEN** a defect lies in a Gradle file, a CI workflow or the harness
- **THEN** the commit says `Case: none` and owes no scenario

### Requirement: A fixed bug adds a chart region only for a condition of its own
A condition that must hold across moves and needs its own exit SHALL become a region or state of the TaskChart (decision 0110).
A wrong edge between existing states SHALL be one edge row.
#### Scenario: A flag that outlives moves
- **WHEN** a defect is a condition surviving several moves and ending only on its own event
- **THEN** the fix adds a region or state with that exit
#### Scenario: A move landing on the wrong state
- **WHEN** a defect is a move taking a wrong edge between two existing states
- **THEN** the fix changes one edge row

### Requirement: A Defect-family commit carries its postmortem
A commit with `Defect-family: <A-M>` SHALL carry `Case: new <scenario> | existing <scenario> | none`
and one trailer each of `Written: <introducing sha>`, `Uncaught: <why no test or review caught it>`
and `Unseen: <why nobody saw it running>`. Each answer names its durable fix: a rule, a test pattern, a guard or a log.
`FixedBugCaseFitness` SHALL fail a Defect-family commit after its cutoff without `Case`,
or touching main code with neither an openspec file nor `Case: existing`.
#### Scenario: A fix without a case
- **WHEN** a Defect-family commit has no `Case` trailer
- **THEN** the rule test names it
#### Scenario: A main-code fix naming a new case without a spec file
- **WHEN** a Defect-family commit touches main code, says `Case: new` and holds no openspec file
- **THEN** the rule test names it
