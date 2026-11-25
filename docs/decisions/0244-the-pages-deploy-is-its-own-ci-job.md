# 0244 — The Pages deploy is its own CI job

[← decisions](README.md) · rule: [`ci`](../rules/ci.md)
on [0176](0176-every-kind-has-a-ceiling.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-25 (JAGT-278). Raises the `ci-jobs` ceiling from 11 to 12.

## Context

- `actions/deploy-pages` lists the run's artifacts once, without a retry; in the job that uploaded them the listing
  can lag, and the release run of 2026-11-25 failed on "No artifacts named github-pages" after a green upload.
- GitHub's own Pages starter workflow deploys in a job that needs the uploading one.

## Decision

Why: the job boundary is the standard shape and lets the upload settle before the listing.

- The `pages` job needs `release`, runs only for a new version, and alone holds the `github-pages` environment.
- `ci-jobs` in `fitness.yml` is 12; `CiWorkflowTest` pins the job's order and permissions.

## Rejected

- A second deploy step after a failed first: a retry the action does not offer, hand-built on `continue-on-error`.

## Reopen when

- A Pages deploy fails again on an artifact lookup across the job boundary.
