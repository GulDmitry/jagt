## Why

The start check held its own view-mode list, seven readers each asked the file system whether `jagt.yml` exists, and the
check of a channel kind named the `command` kind itself.

## What Changes

- The viewer's config owns the view-mode list; the start check asks it.
- One configuration question says whether `jagt.yml` exists; every reader asks it.
- A channel driver names its own configuration faults; the channel check holds no kind.
- The start check's retired keys, shadowing config directory and viewer validation are specified.

## Capabilities

### New Capabilities

### Modified Capabilities

- `startup`: scenarios for retired keys, a shadowing config directory, the viewer, and a channel's faults.
