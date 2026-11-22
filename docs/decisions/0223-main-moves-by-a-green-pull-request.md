# 0223 — Main moves by a green pull request, and CD publishes on the push to it

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) "Git and committing" · relates:
[0189](0189-every-dependency-action-image-and-wrapper-is-pinned.md)

## Status

Accepted 2026-11-22: protection decided by the owner 2026-11-21, CD trigger chosen the same day.

## Context

- `main` took direct pushes, and a release was two of them: the bump, then a tag by hand after a green run.
- A red run on `main` was found after the push; a tag pushed by hand could name a sha CI never passed.
- The nightly could only run on its schedule, so a release waited on the clock.

## Decision

- `main` on GitHub is protected: pull requests only, required checks green and up to date (strict), no force-push, admins included.
- Work reaches `main` as one pull request, pushed as often as needed, merged once green.
- The release is the version bump a green pull request carries; nobody pushes a tag.
- `ci.yml`'s `release` job runs on a push to `main`: the `test` job reads `printVersion` and checks `git ls-remote` for `v<version>`.
- Only when that tag is new, `release` creates it on the pushed sha (`gh release create --target`) and publishes the jar,
  SBOM, attestation, image and Pages; a merge without a bump runs the post-merge checks alone.
- `release` needs `test`, `stand` and `install`; `heavy` ran on the pull request and strict protection keeps that sha current.
- A run on `main` is never cancelled by the next push, so a publish is not cut short.
- The nightly gains `workflow_dispatch`, to start it by hand once the pull request is green.
- `fitness.yml` rows pin the absent tag trigger, the tag-exists guard and the dispatch.

## Rejected

- A tag-triggered release behind a protected `main`: the tag is pushed by hand again, off a sha no rule checked.
- `heavy` in the post-merge run: repeats the pull request's green on the same tree.

## Reopen when

- A merge queue replaces strict protection: the sha the checks saw may then differ from the one pushed.
- A second published artefact needs a version of its own.
