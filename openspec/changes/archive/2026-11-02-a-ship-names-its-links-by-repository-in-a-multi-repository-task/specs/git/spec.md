## MODIFIED Requirements

### Requirement: Ship hands the work to the agent
`ship` SHALL send ONE `ShipService` instruction for every repository holding work (changed in its worktree or ahead of
its target): commit, push, open or update a request against its own target, every link in one `update_agent_status`
(`reviewRequests`; a bare url only for a one-repository task). jagt calls no host and writes no git; the task waits in SHIPPING.
A repository whose `codeHost` is `none` is asked for the push alone and no request, one with no `remote` for neither.
Where every repository is so and none holds unpublished work, REVIEW_PENDING offers the deploy, read from the
configuration and the worktrees each time. A sweep never asks such a repository for a request.
Nothing to ship or deploy is no failure (`NothingToDeployException`, `holdsWork`): passed over and named, no request opened.

#### Scenario: Ship
- **WHEN** `ship ABC-42`
- **THEN** a commit, push and request per repository onto its base
- **AND** where `codeHost` is `none`, a push alone: the session reports REVIEW_PENDING and `deploy` is next
