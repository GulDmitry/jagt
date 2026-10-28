## MODIFIED Requirements

### Requirement: Ship hands the work to the agent
`ship` SHALL send ONE `ShipService` instruction for every repository holding work (changed in its worktree or ahead of
its target): commit, push, open or update a request against its own target, every link in one `update_agent_status`
(`reviewRequests`). jagt calls no host and writes no git; the task waits in SHIPPING.
Nothing to ship or deploy is no failure (`NothingToDeployException`, `holdsWork`): passed over and named, no request opened.
A request is merged when, after a fetch, a merge commit of its target took the branch tip in (`mergedIntoTarget`).

#### Scenario: Ship
- **WHEN** `ship ABC-42`
- **THEN** a commit, push and request per repository onto its base
- **AND** with !571 merged, nothing is shipped and no SHIPPING step is recorded; a squash-merged or never-diverged branch reads as not merged
