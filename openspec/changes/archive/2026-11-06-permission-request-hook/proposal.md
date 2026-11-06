## Why

A permission prompt was told from an idle one by wording inside the Notification, which breaks when the CLI rewords it.

## What Changes

- The runtime declares a typed `permission-request` event; its report marks the task asked, naming the tool.
- `blocking-notification` and `AgentRuntime.blockingNotification` are removed; a Notification is always `idle`.

## Capabilities

### Modified Capabilities

- `attention`: a blocked prompt comes from a typed event, never from wording.
