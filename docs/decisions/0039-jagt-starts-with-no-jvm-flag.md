# 0039 — jagt starts with no JVM flag

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md#interfaces)

## Status

Accepted 2026-10-13.

## Context

Axelix's JVM checks, measured on `jagt-run.jar`, Temurin 25.0.4, an idle board, three starts each:

| flags | start | RSS |
|---|---|---|
| none | 1.16 s | 259 MB |
| `-XX:+UseCompactObjectHeaders` | 1.08 s | 253 MB |
| `-XX:AOTCache` (trained to refresh) | 0.71 s | 423 MB, a 65 MB file per build |
| both | 0.81 s | 435 MB |

jagt starts once and runs for days; its heap holds at most 24 tasks.

## Decision

- `java -jar build/libs/jagt-run.jar` stays the launch, with no flag.
- AppCDS is not measured apart: on 25 the AOT cache supersedes it.
- GC logging stays off; `jcmd <pid> VM.log output=gc.log what=gc` turns it on live when a pause needs explaining.

## Rejected

- The AOT cache: 0.45 s once a session, against a 65 MB file each `stageJar` must retrain and a second launch step.
- Compact object headers: 6 MB of 259, for a flag the one-command start cannot carry.

## Reopen when

- A JDK makes compact object headers the default, or the launch gains a script that can carry a flag.
- jagt's start or memory becomes a complaint.
