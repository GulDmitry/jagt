## MODIFIED Requirements

### Requirement: Facts sit on the thing they are about
Cards SHALL keep their order; only `order: alias` lets a new task take a retired one's place. The filter box (`/`)
matches alias, ticket number or title; `Esc` clears. The task number opens the ticket, the `MR` chip the request; across
repositories each project's name opens its own. The chip shows the request's own age, green with ✓ once approved. The
Deploy button is green while the work is live. A `⚐` beside the title flags the task, in this browser alone.

#### Scenario: Taking the colour off
- **WHEN** a live task is reverted
- **THEN** the Deploy button loses its green
