# 0234 — The live scans are their own job: nightly, or per push on a dependency diff

[← decisions](README.md) · rule: [`ci`](../rules/ci.md) · amends [0233](0233-a-local-green-predicts-ci.md) ·
on [0176](0176-every-kind-has-a-ceiling.md)

## Status

Accepted 2026-11-23, by the owner's order: a CVE published overnight reds the nightly, never an unrelated pull request.

## Decision

- `npm audit` and the osv scan leave `gateTest` and `gateHeavy` for `gateScan`, run by the nightly's `scan` job and the `audit` job.
- The nightly runs `gateScan` always, the `audit` job per push only when a diff against the base touches the dependency set:
  lockfiles, `gradle/libs.versions.toml`, `verification-metadata.xml`, `package-lock.json` (`DependencySet`).
- A base that cannot be diffed scans.
- `ci-local.sh` runs the same `gateScan` through `linux-suite.sh audit`, in the CI image, against `origin/main`.
- GitLab gets an `audit` job: on its schedule, and on a merge request or push whose changed paths hold the dependency set
  (`rules: changes` against `main`), so no engine is left without a scan.
- `installTest` serves the Java runtime from a loopback feed with the real one's redirect;
  the nightly's real-feed install keeps reading the real one.
- `ci-jobs` rises from 9 to 11: one `audit` job on each engine, so the release never waits on a scan and `test` never fails on a new CVE.

## Rejected

- Scans as steps of `heavy`: heavy has no push-to-main run and its local twin cannot carry a base.
- A path filter on a workflow: a required check that never starts blocks the merge.

## Reopen when

- A CVE reds a pull request that changed no dependency, or the diff misses a file that sets one.
