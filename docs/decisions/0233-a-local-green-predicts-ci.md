# 0233 — A local green predicts a CI green, and the nightly owns what local cannot run

[← decisions](README.md) · rule: [`ci`](../rules/ci.md) · supersedes [0226](0226-a-stand-timing-is-judged-against-its-median.md) ·
amends [0139](0139-every-suite-ends-within-one-ceiling.md), [0205](0205-the-installers-are-proven-by-a-ci-job-on-three-systems.md),
[0223](0223-main-moves-by-a-green-pull-request.md) ·
on [0162](0162-a-check-holds-for-all-code.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-23, by the owner's order; amended by 0236 and [0238](0238-every-linux-job-runs-through-one-script-online.md).

## Context

- Since 2026-11-20 `ci.yml` failed 12 of 28 runs: stand (kind) 5, heavy 5, unit + functional 4, install Windows 4, stand compose 2, install macOS 1.
- Failed jobs 2026-11-16..23, by cause: about 11 timing (stand restart, a 10-minute `standTest`, `StandTimingGate`, kitty),
  about 7 environment (POSIX permissions and a fixed 120 s wait on Windows and macOS, a Mac bean on Linux),
  about 6 real defects (checkstyle, `SiteTest`, lockfile, a hover test), 2 infrastructure (apt in the stand image). Real defects were about 23 %.
- The UPGRADE restart variant went red in runs 44342586888, 44898440107 and 45090447340.
- The local gate ran 5 suites; CI ran stands, scans, install and CodeQL too, on 4 vCPUs and 16 GB against a 10-core Mac.
- `ci-local.sh` recorded a green for any argument list.
- Per push, CI read live data (npm audit, osv twice, Adoptium, apt); main re-ran the stand and install.
- The nightly replayed the pull request jobs and added `mutate`.

## Decision

- Six tiers: T0 edit loop, T1 local gate, T2 pull request, T3 main, T4 nightly, T5 weekly and release gate ([`ci`](../rules/ci.md)).
- T1 runs every job T2 runs, by one command, in one pinned image, offline, never capped to a runner's size
  ([0236](0236-a-local-run-is-never-capped.md)).
- CI has no time ceiling, measurement or stop beyond a job timeout; ceilings stay local. `StandTimingGate` is deleted.
- The nightly runs what the default local run leaves out: kind stand, repeated and cold runs, high-tries jqwik, real feeds, live scans.
- Mutation runs on no CI or nightly job: [0151](0151-mutants-are-judged-locally-only.md) holds the rule.
- Every nightly job keeps a local twin by name; a job moved to the nightly changes when it runs, never whether it runs locally.
- Main publishes the tree the pull request proved: the version read and tree equality.
- A CI red after a local green is a divergence defect: a register row, a fix in the local run, never a rerun.
- Windows install is the accepted gap: `PosixFile*` is confined by ArchUnit and `install.ps1` is linted.
- 0226: a median gate is a time gate in CI; superseded. 0139: ceilings are local only. 0205: loopback feed per push, the real one nightly.
- 0223: `release` no longer waits for the stand and install.
- The rule file names each check; `none yet` marks one still to land.
- The `rule-docs` ceiling rises from 8 to 9 for `ci.md`.

## Rejected

- Act on a red automatically (rerun, retry): it hides the divergence and doubles the cost.
- The nightly as a replay of the pull request: it repeats a green and finds nothing local did not.
- A raised timing threshold in CI: a loaded shared runner stays noisy.

## Reopen when

- A merge queue replaces strict protection, or Windows can run locally.
- Real defects are under 10 % of failed CI jobs over a month, or a CI red after a local green recurs from one cause.
