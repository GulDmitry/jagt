# 0220 — CodeQL is the static analysis for security

[← decisions](README.md) · on [0188](0188-osv-scanner-on-the-release-sbom-is-the-vulnerability-scan.md),
[0189](0189-every-dependency-action-image-and-wrapper-is-pinned.md) · standard: OWASP ASVS V14, SLSA build integrity

## Status

Accepted 2026-11-21: dependencies were scanned, jagt's own Java and JavaScript were not.

## Context

- Error Prone and ESLint catch defects, not tainted flows from a request to a sink.
- The repository is on GitHub, where CodeQL is free for public code and uploads to code scanning.

## Decision

- **`.github/workflows/codeql.yml` analyses `java-kotlin` (a Gradle build of main and test classes) and `javascript-typescript`**
  on push and pull request to main.
- **A result of severity error fails the run**: the SARIF is read by `jq`, so the results gate the push.
- **The token is read-only; only `analyse` gets `security-events: write`.** Actions are pinned by sha, a release over two weeks old (0189).
- **Not counted by `ci-jobs` and `ci-triggers`**: they measure `ci.yml`, whose four groups the new workflow does not join.
- **Vendored code is not analysed** (`.github/codeql/codeql-config.yml` ignores `board/vendor`): upstream owns it, 0188 scans it.
- **The known-vulnerability scan is one script** (`scripts/vulnerability-scan.sh`) in both CI files; 0188 is amended.

## Rejected

- Semgrep or SonarCloud: a second account and a rule feed for what CodeQL gives on the platform.
- The default `analyze` exit: it fails on an analysis error only, never on a finding.

## Reopen when

- The code leaves GitHub, or CodeQL's Java queries cannot read the Java 25 build.
