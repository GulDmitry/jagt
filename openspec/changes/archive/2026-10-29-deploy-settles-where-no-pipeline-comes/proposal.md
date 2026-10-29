## Why

A deploy waited forever where the host would never run a pipeline: a project without one, or a repository whose remote is a local path.

## What Changes

- A host listing no pipeline 10 minutes after the push, or a repository with no code-host address, settles the deploy as green.
- A host that does not answer still keeps it open.

## Capabilities

### Modified Capabilities

- `infra-wait`: the deploy pipeline requirement gains the settled-without-a-pipeline outcomes.
