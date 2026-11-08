## Why

The QA check runs the flows its read names, but no read can name one yet.

## What Changes

- With a flows driver set, the accept prompt names the flows repository; the read answers `flows`, the ids checking
  a criterion (a test tagged `@AC-n`, else its title), and `environment`, the listed URL they run on.
- jagt runs those flows after the read; without a driver the prompt names no repository.

## Impact

- Spec: `acceptance-probes`.
- `roles/qa/role.yml.dist`, `roles/qa/brief.md.dist`, `QaVerdict`, `CheckerAnswers`, `CheckerPrompts`, `CheckerReads`.
