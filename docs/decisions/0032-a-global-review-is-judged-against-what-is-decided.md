# 0032 — A global review is judged against what is decided

[← decisions](README.md) · rule: [`guards`](../rules/guards.md), skill `.claude/skills/release-review`

## Status

Accepted 2026-10-09. Its guard rows gather the gaps of 0022 and 0026 to 0031; those records stand as evidence.

## Context

- 0.9.0 to 0.9.1 took 254 commits. Each review round was asked in free words, with no rule, record or earlier
  round handed to it.
- Security rounds three to six each found new spellings of classes already known to pass, so 0026, 0028, 0029,
  0030 and 0031 each listed the gaps again.
- 39 files moved between kinds and `ARCHITECTURE.md` changed 22 times; `DeployTargets`, born under
  `capability/deploy`, moved to `service/` once a job read it. Three rounds each found a test wiring five mocks.

## Decision

- A global review runs `release-review`: every reviewer gets the rules, the records, the guards and the range's
  commit subjects. A finding names its class: new, reopening a record by its Reopen line, or breaking a quoted rule.
  Anything else is dropped.
- `docs/rules/guards.md` is the one list of what each guard passes, by class. A new spelling of a listed class is
  no finding.
- A finding rejected in review gets its why where the next round meets it: a comment at the site, or a record's
  Rejected row.
- A class found in a second round becomes a test. `KindsTest` holds where a class lives, `MockCeilingTest` the three
  mocks a test wires.

## Rejected

- A findings ledger file: the commit subjects already name each finding and its fix.
- A higher review level: more finders re-read the same tree without what was decided.
- A shell parser closing the guards' gaps: rejected in 0028 and 0031.

## Reopen when

- A round's dropped findings include one that later reaches a shared branch or breaks a rule.
- Two consecutive rounds return no finding a test could not have caught.
