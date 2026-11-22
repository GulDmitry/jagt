# 0208 — A fresh install starts with no project, on the files its jar carries

[← decisions](README.md) · on [0204](0204-exit-code-75-means-run-me-again.md),
[0205](0205-the-installers-are-proven-by-a-ci-job-on-three-systems.md)

## Status

Accepted 2026-11-20, owner's choice (JAGT-92).

## Context

- An installed jar refused to start: it read `roles/`, `jagt.desktop.yml.dist` and `jagt.yml` from a root it never had.
- A start with no project was refused, so a board could not be reached to add the first one.
- The install check hid both by copying the files and a project from the repository.

## Decision

- The jar carries each role's `.dist` files, `jagt.desktop.yml.dist` and `mcp_client.js` under `shipped/`, with an index.
- A jar run from `<home>/versions/<v>/` takes `<home>` as its root when none is named, and writes those files into it at every start.
- A shipped file is jagt's own: replaced when its text differs, so an upgrade brings the new text; a human's file is never touched.
- Nothing is written into a root the human named: a checkout's files are never replaced by a jar's.
- No `jagt.yml` is the packaged defaults; adding the first project from the board creates it.
- No project is a start, not a refusal: the board is where one is added.
- The install check copies nothing from the repository.

## Rejected

- Reading the files from the classpath on every access: every reader of `roles/` would change, and a human could not read what a role says.
- Copying only when absent: a role's text would stay at the first install's for ever.
- The launch directory as the root: under a launcher it is wherever the user stands.

## Reopens when

- A file a human edits is shipped, so a start would replace an edit.
