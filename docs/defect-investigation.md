# Defect investigation

[← testing](rules/testing.md) · [spec](../openspec/specs/spec-changes/spec.md)

Every defect is investigated, not only fixed.
The bug task's description and the fix's commit carry these six fields, one trailer each.
The task holds the heavy model's verdict before the fix is built.

| field | answers |
|-------|---------|
| `Kind` | the [`ARCHITECTURE.md`](../ARCHITECTURE.md#the-kinds) kind and sphere the defect sits in |
| `Holds` | today's architecture holds the fix, or the change it needs: a decision in the commit, or a draft for study |
| `Written` | the introducing sha |
| `Uncaught` | why no test or review caught it: the missing test layer |
| `Unseen` | why nobody saw it running |
| `Prevents` | the fitness row, rule test, scenario or guard keeping the class from recurring |

Verdict, one of: the fix holds; a test layer is missing, which; the architecture has a hole, a decision amended or a draft.

```
Kind: <kind>, <sphere>
Holds: <today's architecture | decision NNNN in this commit | draft <name>>
Written: <sha>
Uncaught: <why, and the layer that was missing>
Unseen: <why>
Prevents: <row, test, scenario or guard>
```
