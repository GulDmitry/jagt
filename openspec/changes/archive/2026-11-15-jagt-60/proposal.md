## Why

A `jagt.yml` jagt cannot write ended the project verb in an exception instead of a sentence naming the file.

## What Changes

- A file or folder not writable is refused as an argument naming the file to edit by hand; no temp-and-move is tried
  on a read-only file.

## Impact

- `project-setup`: one requirement and its scenarios modified. Out of scope: the settings form.
