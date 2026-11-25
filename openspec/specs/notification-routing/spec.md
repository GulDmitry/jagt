# notification-routing Specification

## Purpose
Which facts jagt tells, to which channel, in whose words, and once.

## Requirements

### Requirement: A point is routed to the channels jagt.yml lists, told once per key
A point is a fact that stands (`flow/role/Point`): an id and what makes it stand. `notifications.channels` SHALL name each channel (kinds `desktop`,
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

### Requirement: A teams channel posts an Adaptive Card to a Workflows webhook
A channel of kind `teams` SHALL post the point's title and text as an Adaptive Card within 10 s. The URL is held in the environment variable
its `webhookEnv` names; each link in the text is an `Action.OpenUrl` action. An answer other than 2xx, an unreachable host or an unset
variable is a failure naming the host, never the URL, which is a secret; the channel's retry and report apply.
A `teams` channel with no `webhookEnv` SHALL refuse startup.
#### Scenario: A point is told to Teams
- **WHEN** `deployed` is routed to a `teams` channel and its text holds `http://localhost:8290/?task=ABC-42`
- **THEN** one card with that title and text posts, with one `Action.OpenUrl` action for the link
- **WHEN** the webhook answers 400
- **THEN** the failure names the host and the status, and the point stays untold

### Requirement: A link names the board's public URL
A link jagt sends to a human (banner, channel message, startup banner) SHALL start with `server.publicUrl` when it is set,
else with `http://localhost:<port>`.
The URLs a session reaches jagt by (mcp, hook, gate) SHALL stay loopback.

#### Scenario: A public URL is set
- **WHEN** `server.publicUrl` is `https://jagt.example.org` and a notification names `ABC-42`
- **THEN** its link is `https://jagt.example.org/?task=ABC-42`

#### Scenario: No public URL
- **WHEN** `server.publicUrl` is blank
- **THEN** links name `http://localhost:<port>` as before

### Requirement: A post the delivery backlog rejects is retried
A post the full delivery backlog refuses SHALL count as a channel that cannot be reached. It stays pending.
One WARN and one board-head mark name channel, point and cause `channel backed up`. It is retried on the same backoff until it lands.
#### Scenario: The backlog is full
- **WHEN** a point is posted while the delivery backlog is full
- **THEN** it is not dropped; one board-head mark names the channel and the cause
- **AND** it is retried after 10 s and delivered once the backlog accepts it
