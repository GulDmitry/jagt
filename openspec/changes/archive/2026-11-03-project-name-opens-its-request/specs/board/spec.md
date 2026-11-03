## MODIFIED Requirements

### Requirement: Facts sit on the thing they are about
The task number SHALL open the ticket. Where the board names projects, each project's name opens its own request;
on a board of one project the `request` chip does. The chip shows the request's own age, green with ✓ once approved.
The Deploy button is green while the work is live. A `⚐` beside the title flags the task, in this browser alone.

#### Scenario: Taking the colour off
- **WHEN** a live task is reverted
- **THEN** the Deploy button loses its green

#### Scenario: A one-repository card on a board of several projects
- **WHEN** ABC-42 in `alpha`, its request open 8h, shares the board with a task in `beta`
- **THEN** its card reads `alpha 8h`, the name opening the request, and no `request` word
