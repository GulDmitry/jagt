# 0165 — Error Prone keeps the Java the build targets

[← decisions](README.md) · rule: [`style`](../rules/style.md) · builds on [0162](0162-a-check-holds-for-all-code.md)

## Status

Accepted 2026-11-14.

## Context

- Owner 2026-11-11: the IDE marks code that could use `instanceof` patterns, switch expressions and single
  expressions; the tree should speak Java 25 and keep speaking it.
- Error Prone 2.51.0 is already in every module's compile (`jagt.conventions`, `-Werror`, all source sets):
  its default `PatternMatchingInstanceof` and `StatementSwitchToExpressionSwitch` warnings fail the build
  on a hand cast after `instanceof` and on a statement switch an arrow form can replace.
- OpenRewrite 7.39.0 with `rewrite-migrate-java` and `rewrite-static-analysis` (recipe BOM 3.37.0, checked
  2026-11-14), run at its defaults over every module, found one site: a block lambda in `McpProtocolService`.

## Decision

- Error Prone's existing checks are the gate: no new tool, no new task, no configuration.
- The one block-lambda site is rewritten by hand to the recipe's patch.

## Rejected

- OpenRewrite `rewriteDryRun` in `test`: it needs `failOnDryRunResults`, loses project classpaths under
  `org.gradle.parallel` and the configuration cache, and its one remaining catch is a single lambda form.
- An own Error Prone or PMD check for record patterns: no standard check exists; own code needs its own decision.

## Reopen when

- Error Prone or a recipe ships a record-pattern check, or a block lambda lands twice more.
