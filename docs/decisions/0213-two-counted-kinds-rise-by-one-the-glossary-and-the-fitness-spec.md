# 0213 — Two counted kinds rise by one: the glossary and the fitness spec

[← decisions](README.md) · on [0176](0176-every-kind-has-a-ceiling.md),
[0209](0209-one-fitness-yml-declares-every-fitness-function-in-a-reserved-tree.md)

## Status

Accepted 2026-11-20. Records two raises of 0176 that landed without their record.

## Context

- 0176: raising a number needs a decision record in the same commit.
- `rule-docs` went 7 to 8 with `docs/rules/glossary.md`, `spec-capabilities` 50 to 51 with the `fitness` spec; neither commit held one.

## Decision

- **`rule-docs` is 8**: the glossary is a rule of its own, one definition per term; folded into `style.md` it would pass the rule budget.
- **`spec-capabilities` is 51**: `fitness` is a capability of its own, with its own requirements and no other spec to hold them.
- Both stay ratchets: above or below the number fails.

## Rejected

- Folding the glossary into `style.md`: its tables outgrow the 100-line rule budget.
- Folding `fitness` into `engineering`: that spec names fitness function ids and owns none of the behaviour.

## Reopen when

- A term table moves out of `docs/rules/`, or a spec is merged into another.
