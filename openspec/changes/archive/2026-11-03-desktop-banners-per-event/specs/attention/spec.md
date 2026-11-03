## ADDED Requirements

### Requirement: Desktop banners are chosen per event
Each notification SHALL carry one event: `your-move`, `auto-review`, `stopped`, `housekeeping`, `install`, `usage`,
`request-opened` or `deployed`. The desktop channel SHALL raise a banner only for an event `notifications.desktop` lists
(`DesktopNotifier.takes`); unset, every event but `deployed`; an unknown id is ignored. `MilestoneJob` SHALL raise
`request-opened` (naming the requests) when a status turns into `CI_POLLING`, and `deployed` when it turns into `DEPLOYED`;
a task first seen, a restart included, is silent.
#### Scenario: A request opens, a deploy lands
- **WHEN** ABC-42 turns to CI_POLLING with `notifications.desktop` unset
- **THEN** one banner names the task and its request links
- **WHEN** it turns to DEPLOYED and `notifications.desktop` leaves `deployed` out, or is unset
- **THEN** no banner is raised; listing `deployed` raises one
#### Scenario: jagt restarts
- **WHEN** ABC-42 already stands at CI_POLLING or DEPLOYED as jagt starts
- **THEN** nobody is pinged
#### Scenario: An event left out
- **WHEN** `notifications.desktop` is `[stopped]` and a usage window fills
- **THEN** no desktop banner is raised
