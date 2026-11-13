## ADDED Requirements

### Requirement: A point is routed to the channels jagt.yml lists, told once per key
A point is a fact that stands (`flow/Point`): an id and what makes it stand. `notifications.channels` SHALL name each channel (kinds `desktop`,
`teams`, `command`) and `notifications.points` route a point to channels, each route with its own title and text. A point nobody routes is told to
nobody. `PointWatch` SHALL observe each persisted write and tell a standing point once per (task, point, key), in `ToldOnce.points`; one standing at
start is told silently.
#### Scenario: One point, two channels
- **WHEN** `deployed` is routed to `desktop` and `team` and ABC-42 deploys
- **THEN** each channel gets its own text once; a restart tells nothing again
- **WHEN** `jagt.yml` holds no `notifications.points` route
- **THEN** jagt starts with one note that nothing will be told
- **WHEN** a route names an unknown point, channel, kind or placeholder, or a `webhookEnv` that is unset
- **THEN** startup refuses with every problem in one report
- **WHEN** a channel cannot be reached
- **THEN** one WARN `notification not delivered` and one board-head mark name channel, point and cause; the point stays untold
- **AND** it is retried after 10 s, 20 s, then every 30 s; the first success ends both

### Requirement: A role declares its points
`roles/<id>/role.yml` `points` SHALL list `passed`, `round-sent` or `declined`; each stands as `<id>-<kind>`. A disabled role contributes no points
and a route naming one is inert, with one startup WARN.
#### Scenario: A role is disabled
- **WHEN** acceptance is disabled and a route names `acceptance-passed`
- **THEN** it is never told, and startup warns once
