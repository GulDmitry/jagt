## ADDED Requirements

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
