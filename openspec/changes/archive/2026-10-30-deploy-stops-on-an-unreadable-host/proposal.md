## Why

A deploy whose pipeline the host could not list waited with no ceiling: nothing told the human the host was down.

## What Changes

- Three deploy reads running that cannot list a target's pipeline stop the task as `DEPLOY_UNREAD`.
- The stop ends on `DEPLOY_READ`: the deploy check keeps reading while it stands, and the first read that lists
  every pipeline ends it.

## Capabilities

### Modified Capabilities

- `infra-wait`: the deploy pipeline requirement.
