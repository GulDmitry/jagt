## MODIFIED Requirements

### Requirement: An unreadable pipeline host stops the deploy
A host that does not answer keeps the deploy open. A read that reached the host and could not list a pipeline counts:
a timeout, a 5xx, output that is not JSON. Three reads running SHALL stop it as `DEPLOY_UNREAD`.
The first read listing every pipeline, run while that stop stands, SHALL end it.

#### Scenario: The deploy pipeline cannot be listed
- **WHEN** the host answers 500 three reads running on ABC-42's deploy commit
- **THEN** ABC-42 stops as `DEPLOY_UNREAD`; the first read listing every pipeline ends the stop

## ADDED Requirements

### Requirement: A deploy waits on a reach jagt lost
A read jagt could not make at all SHALL keep the deploy open as `waiting on its pipeline`.
Such a read is a CLI missing, signed out or refused, or a host unreachable. It counts toward no stop and resets no count.
That is jagt's lost reach: held per driver and host in memory, ended by the next read through that pair that succeeds.
A retry waiting for its time stays armed through it.

#### Scenario: The CLI is signed out
- **WHEN** `glab` is signed out for three reads running
- **THEN** ABC-42 stays deployed, its line says `waiting on its pipeline`, and the header carries the lost reach

#### Scenario: Reads go on while reach stands
- **WHEN** reach to `git.example.invalid` is lost and the human signs in
- **THEN** the next tick's read lists the pipeline and clears it

### Requirement: A project names its login
A `command` project MAY declare `loginCommand`, the argv the `login` verb opens; a `pipelineCommand` or `retryCommand`
exiting 77 SHALL read as signed out. `github` and `gitlab` name their own CLI's login on the host read.

#### Scenario: A command signed out
- **WHEN** `pipelineCommand` exits 77 and `loginCommand` is `["ci-login", "--web"]`
- **THEN** the lost reach names `ci-login --web` as the fix
