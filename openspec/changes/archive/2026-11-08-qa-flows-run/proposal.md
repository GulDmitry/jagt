## Why

The flows port exists but nothing calls it; the QA check must run the flows its read asks for, on a listed environment.

## What Changes

- After a QA read, jagt runs the flow ids the read named on the environment it named (no read names one yet, so nothing runs); each result is a row of `qa-report.md`.
- The allow-list is `roles.qa.environments` alone; `flows.environments` goes, and `flows` sits under `roles.qa`.
- An unknown `flows.driver` is refused at start.

## Impact

- Spec: `acceptance-probes`.
- `usecase/job/CheckerFlows`, `CheckerReads`, `adapter/flows/*`, `jagt.yml.dist`, `docs/features.md`.
