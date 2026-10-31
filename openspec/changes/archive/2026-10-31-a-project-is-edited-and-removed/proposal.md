## Why

A project could be added from the board but only changed or dropped by hand in `jagt.yml`.

## What Changes

- `PUT /api/projects/{key}` rewrites the managed keys of a project's block; its other keys and comments stay.
- `DELETE /api/projects/{key}` and `project remove <key>` delete the block, refused while an open task works in it.
- A file that cannot be written is refused naming it, and stays as it was.

## Capabilities

### Modified Capabilities

- `project-setup`: edit and remove, and the refusal of an unwritable file.
