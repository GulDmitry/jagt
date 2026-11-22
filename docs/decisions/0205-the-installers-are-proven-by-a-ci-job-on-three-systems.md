# 0205 — The installers are proven by a CI job on three systems

[← decisions](README.md) · on [0176](0176-every-kind-has-a-ceiling.md), [0204](0204-exit-code-75-means-run-me-again.md) ·
rule: [`release-artifacts`](../../openspec/specs/release-artifacts/spec.md)

## Status

Accepted 2026-11-20.

## Context

- The installers, the launcher, the release check and `upgrade` were each tested alone, on the machine that wrote them.
- The first end-to-end run found two defects.
- The runtime's checksum URL was built from a signed storage URL.
- The launcher's jar path ran through `current`, so a running jagt found no layout to keep a release in.

## Decision

- **A GitHub `install` job runs `scripts/install-check.sh` on Linux, macOS and Windows**, per push and pull request.
- **The release waits for it.**
- **The script serves a fake feed on loopback** and installs `0.0.1` with the platform's installer (`install.ps1` on Windows).
- **It starts the launcher on a throwaway root** and lets the release check keep `0.0.2`.
- **It runs `upgrade` through the board's command endpoint** and waits for the restart.
- **The runtime is Adoptium's real feed**; nothing else leaves the machine.
- **Every wait is bounded**, the script ends its processes and temp folder, and a failure names its stage.
- **Ceilings** ([0176](0176-every-kind-has-a-ceiling.md)): `ciJobs` 9 for the `install` job; GitLab has none: its runners are Linux.

## Rejected

- A test-only interval key for the release check: the check already runs at startup and by `run releasecheck`.
- A mocked runtime feed: the redirect to signed storage is the part that broke.

## Reopen when

- A run costs more than ten minutes on a runner, or GitLab gains a macOS or Windows runner.
