# 0252 — build-logic reads YAML with SnakeYAML

[← decisions](README.md) · on [0176](0176-every-kind-has-a-ceiling.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-25.

## Context

- `fitnessRaises` compares each `fitness.yml` row with its merge base, so `build-logic` parses YAML.
- SnakeYAML is the parser `usecase` and `surface` already use.

## Decision

- `build-logic` declares `org.yaml:snakeyaml`; `dependency-lines-in-build-gradle` rises from 98 to 99.

## Rejected

- Parsing rows by regex: own code for a job a standard library does.

## Reopen when

- `build-logic` drops its last YAML read.
