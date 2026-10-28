## Why

A long-lived task branch off a moving `release/x` made its request list commits the target already held, and the
session was forbidden to rebase
([0099](../../../../docs/decisions/0099-the-session-rebases-its-own-branch-onto-the-moved-target.md)).

## What Changes

- The session rebases its own branch onto its request's target when the target moved on and pushes it with
  `--force-with-lease`; the `ship` step orders it before the push.

## Capabilities

### Modified Capabilities

- `git`: the one rewrite is the task's own branch onto its target, by jagt or by its session.
