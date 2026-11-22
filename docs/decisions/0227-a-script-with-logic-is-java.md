# 0227 — A script with logic is Java

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) "Code" · kind: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-kinds) ·
on [0162](0162-a-check-holds-for-all-code.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md),
[0205](0205-the-installers-are-proven-by-a-ci-job-on-three-systems.md)

## Status

Accepted 2026-11-22.

## Context

- Two stored scripts held logic that no gate of the build reaches: the change-to-tests selection (216 lines of case, loops and awk)
  and the installer proof (159 lines, a Python helper for the feed, the port and the timeout).
- Java has Checkstyle, cognitive complexity, duplication, JUnit and mutation testing here; shell has none of them.
- Google's Shell Style Guide sends a script past 100 lines, or with non-straightforward control flow, to a structured language.
- Measured 2026-11-22 on a macOS laptop with JDK 25: a JVM starts in 0.04–0.05 s; `./gradlew -q testsFor` takes 0.7 s on a warm daemon,
  4 s on the first call and 6.9 s with `--no-daemon`.
- The task and the old script, run on the 640 paths of `release~80..release` with byte-order sorting, print the same lines
  but for one: the build-logic tests.

## Decision

- **A script with logic is Java**: a `build-logic` task, or a JUnit suite in its own source set, under the build's floor.
- **Shell stays** where no JVM runs yet (`install.sh`, `install.ps1`, the container entrypoint), where a JVM is wrong
  (a fake CLI a suite spawns), or as a one-call wrapper of a tool; `mcp_client.js` stays a per-session Node bridge.
- **Speed decides the edge**: a script stays shell when Java's start breaks its latency budget for how often it runs.
- **Row `scripts-are-java`** fails a tracked `.sh`, `.ps1`, `.bash`, `.py` or `.js` outside its allow-list; `board/` is a web UI and joins it.
- **`tests-for.sh` becomes `./gradlew -q testsFor`**; it also selects the build-logic tests, which the script never did.
- **`install-check.sh` becomes the `installTest` suite**; the `install` job runs it on three runners
  ([0205](0205-the-installers-are-proven-by-a-ci-job-on-three-systems.md)).
- **The script's order of `--tests` names was its shell's locale order**; the task sorts paths by bytes.
- **Ceilings** ([0176](0176-every-kind-has-a-ceiling.md)): `source-sets` 27, `build-logic-files` 24, `dependency-lines-in-build-gradle` 98.
- **`Git`, the task's own wrapper, joins `spawn-owner`'s exception**: a build task has no server whose stop reaps children.

## Rejected

- `java Script.java` for the selection: it runs outside the build's tests and checks.
- A Gradle module for the task's classes: the module ceiling and the module map grow for one task.
- Python for the fake feed: a second language and an interpreter on three runners; the JDK's own HTTP server needs neither.

## Reopen when

- A script on the allow-list passes 100 lines or gains control flow.
- A warm `testsFor` takes longer than 3 s.
