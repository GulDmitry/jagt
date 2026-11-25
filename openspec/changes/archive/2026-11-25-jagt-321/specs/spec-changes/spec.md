## ADDED Requirements

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
