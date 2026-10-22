## MODIFIED Requirements

### Requirement: Ship hands the work to the agent
`ship` SHALL send ONE `ShipService` instruction for every repository holding work (changed in its worktree or ahead of
its target): commit, push, open or update a request against its own target, every link in one `update_agent_status`
(`reviewRequests`). jagt runs no git, calls no host; the task waits in SHIPPING. Nothing to ship
or deploy is no failure (`NothingToDeployException`, `holdsWork`): passed over and named, no request opened.
A session that rebased onto a new base on the human's or the Master's word SHALL report it with `retarget_task`;
jagt records it (`TaskRetarget`) only once origin has the branch and every worktree's HEAD holds it.

#### Scenario: Ship
- **WHEN** `ship ABC-42`
- **THEN** a commit, push and request per repository onto its base

#### Scenario: A task stacked on another
- **WHEN** ABC-42's session rebases onto `ABC-41` and calls `retarget_task` with it
- **THEN** ship targets `ABC-41`, and the diff is read against it

#### Scenario: A base the worktree does not stand on
- **WHEN** `retarget_task` names a branch origin lacks, or one HEAD does not hold
- **THEN** it is refused and the base stays
