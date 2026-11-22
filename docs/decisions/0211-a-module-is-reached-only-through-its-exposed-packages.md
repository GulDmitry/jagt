# 0211 — A module is reached only through its exposed packages

[← decisions](README.md) · on [0209](0209-one-fitness-yml-declares-every-fitness-function-in-a-reserved-tree.md),
[0176](0176-every-kind-has-a-ceiling.md), [0162](0162-a-check-holds-for-all-code.md)
Rule: [`fitness`](../../openspec/specs/fitness/spec.md)

## Status

Accepted 2026-11-20, owner's order of the same day.

## Context

- The rings rule pins the direction between Gradle modules; nothing pins what a module lets another reference.
- A module's API is a choice, not a fact the compiler holds: every public class is reachable.

## Decision

- **A module's API is its exposed packages**, declared in `fitness.yml` under `project.modules`, `..` for every package.
- **One declared fitness function counts the leaks**: a class of one declared module referencing a type of another outside its exposed packages.
- **The engine is ArchUnit's modules API**, already on the stack: no new dependency.
- **The library names no module**: the list and the packages live in the project's `fitness.yml`.
- **A module without an `exposes` list fails the load**, so no module is exposed by omission.
- **The shared kernel and the use cases expose every package**; the assembly root is no declared module and may reach all.
- **The count enters at its measured number and ratchets** ([0176](0176-every-kind-has-a-ceiling.md)).

## Rejected

- JPMS `module-info`: Spring and Lombok reflection would need `opens` on every package.
- A rule on `port..` alone: an adapter class a sibling module needs is no port.

## Reopen when

- A module's leaks stay above zero for a release without a fix or a port.
