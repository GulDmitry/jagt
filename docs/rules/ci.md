# CI tiers

[← testing](testing.md) · why and evidence: [0233](../decisions/0233-a-local-green-predicts-ci.md)

A local green predicts a CI green. CI adds no check the local gate lacks and takes no measurement. Suites: [testing-suites](testing-suites.md).

## The tiers

| tier | what belongs | command | runs | a red means |
|---|---|---|---|---|
| T0 edit loop | the files changed, mutants on the diff, 2 minutes at most | `./gradlew -q testsFor`, `pitestDiff` | the session, per change | fix before the commit |
| T1 local gate | every job a pull request runs, in CI's image, runner-sized, offline, time ceilings on | `ci-local.sh`, jobs by name | the session, before a push | fix before the push |
| T2 pull request | T1's commands, Windows and macOS install, CodeQL; a job timeout and no other limit | one `./gradlew` line or script per job | GitHub, per push to a request | a divergence when T1 was green |
| T3 main | publish the tree T2 proved: the version read and tree equality with the request head | `release` job | GitHub, on a merge | a publish failure |
| T4 nightly | what the default T1 run leaves out: long, repeated, cold, live | `nightly.yml` jobs, each with a local twin | GitHub, once a night | an issue opens (`nightly` label), never a rerun |
| T5 weekly and release gate | base images, Scorecard, live stand, `promptEval`, `roleEval` | named workflows, `standTest` | schedule, the release | a task opens before the release |

## The stability policy

Each rule names its check. `none yet` is a gap, open until the check lands.

1. **CI has no time ceiling, measurement or stop**; only the job's `timeout-minutes` bounds a hang. `ci-jobs-without-timeout`,
   `ci-no-time-flags`; the ceilings are local, on by default, and soft: an overrun finishes and warns, a hang guard of 3x stops
   ([0139](../decisions/0139-every-suite-ends-within-one-ceiling.md)).
2. **One `./gradlew` line or script per CI job**, the one the local gate runs. `CiWorkflowTest`.
3. **Every CI and nightly job has a local twin**, selectable by name: `ci-local.sh nightly-<job>`, or `nightly` for all.
   Windows install and the real-feed install are the exceptions: the first by a manual dispatch, the second by `install-macos`.
   `CiWorkflowTest`: each nightly job runs `scripts/linux-suite.sh nightly-…` and `gate()` holds its line; `linux-suite.bats`.
4. **The recorded green is the whole gate**: only the argument-free default run records `ci-local.ok`. none yet.
5. **The CI image is one, pinned by digest**, used by CI and the local gate; no `ci.yml` job installs a package.
   `ci-image-pinned-by-digest`, `ci-no-package-install`, `CiWorkflowTest`.
6. **The local gate is runner-sized and offline**; parallelism comes from the machine, never fixed. `linux-suite.bats`: the container is
   capped by `scripts/runner.env`, the run has `--network none` after an online warm-up, each run has its own Gradle home;
   `standParallelism` and `e2eForks` read the available processors. `install` keeps its network until the Java runtime feed has a
   loopback twin (rule 7).
7. **A per-push check reads no live data**: a scan or feed read needing the network runs nightly, or per push on a dependency diff
   (`gateScan -Paudit.base`; `installTest` serves the runtime from loopback).
   The local `audit` also builds and scans each base image for amd64 (`baseImageScan`)
   when `base/` changed: base-images.yml's twin. `ci-per-push-reads-no-live-scan`, `CiWorkflowTest`.
8. **A wait is a hang guard** from one shared constant, never a speed assert. `sleep-in-tests`, `ci-no-sleep`, `wait-literal-in-tests`.
9. **A rerun or retry never turns a CI red green.** `ci-no-retry`.
10. **The nightly runs only what T1 does not**; no nightly job repeats a pull request job unchanged.
    `nightly-calls-no-pull-request-job`; the rest: none yet.
11. **Main publishes the tree the pull request proved**; the stand and install do not re-run there.
    `release-needs-no-stand-or-install`, `CiWorkflowTest`, `ProvenTree`.
12. **A change to CI or the local twin runs first as one job alone on a throwaway branch**: a dispatch with input `job`,
    never first on the release request. `CiWorkflowTest`.

## The divergence rule

- A CI red after a local green is a defect in the local run, not in CI and not a flake.
- It gets a row the same day in the [divergence register](../divergences.md): date, run id, recorded-green tree, class
  (why the local run missed it), the fix commit. `divergenceRegister` fails while a red run of a recorded-green tree has none.
- It is fixed in the local run, so the same cause turns the next local run red.
- It is never rerun to see whether it passes ([flaky-test](testing.md)).
- A cause only the real machine shows (a Windows permission, a runner's load) is named in the row, and T2 keeps that job.
