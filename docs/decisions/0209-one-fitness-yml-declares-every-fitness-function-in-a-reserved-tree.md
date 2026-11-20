# 0209 — One `fitness.yml` declares every fitness function in a reserved tree

[← decisions](README.md) · on [0162](0162-a-check-holds-for-all-code.md) ·
amends [0176](0176-every-kind-has-a-ceiling.md), [0186](0186-the-engineering-capability-is-the-one-inventory-of-gates.md)
Rule: [`fitness`](../../openspec/specs/fitness/spec.md)

## Status

Accepted 2026-11-20, owner's order of 2026-11-18.

## Context

- Fitness functions (Ford, Parsons, Kua, Sadalage, *Building Evolutionary Architectures*, 2nd ed., ch. 2) are spread over test classes,
  constants, Gradle files and one JSON; no one reads them as a set.
- The book asks for no number; jagt's fitness functions each need one to ratchet.

## Decision

- **One file, `fitness.yml`**: `schema: 1`, `extends: defaults`, `project`, then the reserved tree; 
  `defaults.yml` holds the generic ones.
- **The tree is reserved whole, empty where jagt has no fitness function**: the book's four dimensions (technical, data, security, operational),
  then ISO/IEC 25010:2011 characteristics and subcharacteristics, data from ISO/IEC 25012
  inherent. Functional suitability stays out: the book keeps fitness functions out of the domain.
- **A section says what a fitness function protects; `governs` says how**, from a closed list: coupling, abstractness-instability, import-direction,
  complexity, license, accessibility, package-dependency, class-dependency, inheritance, annotation, layer, linter, communication, chaos,
  security-scan, size, count. The engine is the fitness function's attribute, never a section.
- **Every fitness function is a number with a direction, `max` or `min`**; a zero fitness function is `max 0`.
  The report prints id, value, threshold and every item.
- **Validation on load**: an unknown key, wrong type, missing threshold, unknown id, unknown section or unknown `governs` fails before any
  fitness function runs with a typed `FitnessConfigException` naming the key.
- **The 0176 ratchet holds for every fitness function with a stored number**:
  above fails, below fails asking to lower it; raising needs a decision record.
- **A coded fitness function is a `Fitness` class found by id through `ServiceLoader`**, never a script in the config.
- **YAML is read by snakeyaml**, already in the lockfile: no new dependency.
- **Amends 0176**: its numbers move from `complexity-ceilings.json` into `fitness.yml`; its ratchet and kinds stay.
- **Amends 0186**: the engineering spec names fitness function ids, `fitness.yml` holds selectors and numbers.

## Rejected

- Sections by engine (ArchUnit, PMD, Gradle): the engine changes, what a fitness function protects does not.
- A script field in the config: a hard contract beats a soft one.
- Adding a tree section on demand: a section is a schema change and a decision.

## Reopen when

- A fitness function fits no subcharacteristic, or ISO/IEC 25010 is revised.
