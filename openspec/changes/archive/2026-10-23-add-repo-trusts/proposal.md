## Why

add-repo recorded no git trust for the repository it attached, so the next ship read that repository's whole existing
config as changed since trusted and refused until the human pressed trust.

## What Changes

- add-repo adds the attached repository's git state to the task's trusted record as it stands, the others untouched.

## Capabilities

### New Capabilities

### Modified Capabilities

- `git`: add-repo trusts the repository it attaches.
