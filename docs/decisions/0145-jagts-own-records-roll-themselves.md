# 0145 — jagt's own records roll themselves

[← decisions](README.md) · rule: [`components`](../rules/components.md)

## Status

Accepted 2026-11-07, recorded retroactively.

## Context

- `misses.jsonl`, `stops.jsonl` and `interactions.jsonl` are jagt's data: a line per event, read back by its own
  readers and by a human, beside `state.json` in the state directory.
- `service/RollingFile` keeps each under 1 MiB with one `.1` generation; Logback's `RollingFileAppender` does the same job.

## Decision

- A record jagt reads back is written by `RollingFile`, never through a logger.

## Rejected

- Logback appenders per record: the state directory is known only once `jagt.yml` is read, a data file would hang on
  the logging config, and a log level or pattern change could drop or reshape a record.

## Reopen when

- A record needs more than one rolled generation, rolling by time, or compression.
