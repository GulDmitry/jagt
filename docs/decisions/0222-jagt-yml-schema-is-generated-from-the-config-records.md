# 0222 — The jagt.yml schema is generated from the config records

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) · relates: [0130](0130-every-third-party-is-a-configured-seam.md),
[0189](0189-every-dependency-action-image-and-wrapper-is-pinned.md)

## Status

Accepted 2026-11-21. Reopens on the condition below.

## Context

- `jagt.yml` had no schema: a mistyped value in a shipped `.dist` file surfaced at startup.
- Its keys live in two places: `ConfigFile`, re-read on every access, and the `@ConfigurationProperties` records Spring binds once.
- Spring's configuration metadata covers the second place alone, never projects, roles, tracker or agent.

## Decision

- `ConfigReferenceTest` generates the schema from both sets of records with victools `jsonschema-generator`, test scope, nothing committed by hand.
- Every object is closed: a key no record holds fails validation; `server:` stays open, as Spring's own.
- A record field renamed by `@JsonProperty` takes that name; a `@JsonUnwrapped` field's keys move up one level.
- Both `.dist` files are validated against it with networknt `json-schema-validator`, test scope.
- Both libraries are pinned like every dependency (0189); no build-logic plugin, no annotation processor.

## Rejected

- Spring configuration metadata merged by a build-logic plugin: lacks the `ConfigFile` keys.

## Reopen when

- A record binds a key the generator cannot express, such as a polymorphic value.
