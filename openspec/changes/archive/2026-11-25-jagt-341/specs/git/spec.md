## MODIFIED Requirements

### Requirement: Ship hands the work to the agent
`ship` SHALL send ONE `ShipService` instruction for every repository holding work (changed in its worktree or ahead of
its target): commit, push, open or update a request against its own target, every link in one `update_agent_status`
(`reviewRequests`; a bare url only for a one-repository task). jagt calls no host and writes no git; the task waits in SHIPPING.
A repository whose `codeHost` is `none` is asked for the push alone and no request, one with no `remote` for neither.
A task with a ticket gets its request title from the pattern, key and summary; a task with none is told to write one short
imperative sentence from the change, never its branch name nor its instructions cut short.

#### Scenario: Ship
- **WHEN** `ship ABC-42`
- **THEN** a commit, push and request per repository onto its base
- **AND** where `codeHost` is `none`, a push alone: the session reports REVIEW_PENDING and `deploy` is next

#### Scenario: A task no ticket names
- **WHEN** `ship` runs on a task with no ticket link
- **THEN** the instruction asks the agent for one short imperative sentence describing the change, as the first commit's message and the request's title
- **AND** it carries neither the task's name nor its instructions cut by an ellipsis

#### Scenario: No request to open
- **WHEN** every repository has `codeHost` `none` or no `remote`, and none holds unpublished work
- **THEN** REVIEW_PENDING offers the deploy, read from the configuration and the worktrees each time; a sweep never asks them for a request
- **AND** nothing to ship or deploy is no failure (`NothingToDeployException`, `holdsWork`): passed over and named, no request opened
