## MODIFIED Requirements

### Requirement: Ship hands the work to the agent
`ship` SHALL send ONE `ShipService` instruction for every repository holding work (changed in its worktree or ahead of
its target): commit, push, open or update a request against its own target, every link in one `update_agent_status`
(`reviewRequests`). jagt calls no host and writes no git; the task waits in SHIPPING.
A repository whose `codeHost` is `none` is asked for the push alone and no request; where every repository is so, the session
reports REVIEW_PENDING, the deploy is offered, and no sweep waits. Further work clears that mark, and a request linked later replaces it.
Nothing to ship or deploy is no failure (`NothingToDeployException`, `holdsWork`): passed over and named, no request opened.

#### Scenario: Ship
- **WHEN** `ship ABC-42`
- **THEN** a commit, push and request per repository onto its base

#### Scenario: Ship with no code host
- **WHEN** `ship ABC-42` in a repository whose `codeHost` is `none`
- **THEN** the session pushes the branch, opens no request and reports REVIEW_PENDING; `deploy` is next
