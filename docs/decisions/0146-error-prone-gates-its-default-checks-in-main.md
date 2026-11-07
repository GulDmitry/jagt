# 0146 — Error Prone gates its default checks in main

[← decisions](README.md) · rule: [`testing`](../rules/testing.md)

## Status

Accepted 2026-11-07.

## Context

- Error Prone ran NullAway alone; its own bug checks (reference equality, string splitting, ignored futures) were off.
- The first run with them on found 73 findings in main, none needing a suppression.

## Decision

- Three linters, three jobs: Error Prone = bugs, PMD (`CognitiveComplexityTest`, `DuplicationTest`) = complexity and
  duplication, Checkstyle = counts and imports ([0143](0143-checkstyle-gates-parameter-and-component-counts.md)).
- `compileJava` runs Error Prone's default checks beside NullAway; `-Werror` makes its warnings fail the build.
  Test source sets stay off.
- Measured 2026-11-07, all fixed by code: OperatorPrecedence 25, StringSplitter 13, ReferenceEquality 8, NotJavadoc 4,
  InlineFormatString 4, UnusedVariable 3, MissingSummary 2, FutureReturnValueIgnored 2, ArrayRecordComponent 2,
  one each of UnusedTypeParameter, ThreadLocalUsage, MissingOverride, LoopOverCharArray, JdkObsolete, InvalidParam,
  InvalidLink, EnumOrdinal, BoxedPrimitiveEquality, AvoidCommonTypeNames.
- Cost: `compileJava --rerun-tasks` about 8-12 s without, 17-21 s with (estimate, two runs each, all modules).
- Zero suppressions: a check a site cannot satisfy is fixed in the code or taken to the owner.

## Rejected

- `@SuppressWarnings` or disabling a check: the owner's rule is zero.

## Reopen when

- A default check yields noise a code change cannot remove.
