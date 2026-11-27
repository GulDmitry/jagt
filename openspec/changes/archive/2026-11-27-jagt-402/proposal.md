## Why

A session told to ship a clean tree whose request was open and green handed back REVIEW_PENDING, so the task waited at
VERIFYING; no eval read a ship turn of the session brief.

## What Changes

- A ship turn of a task whose request is open ends only in CI_POLLING or a question; `FlowRules.refusedAtShip` refuses any other report.
- The brief's `ship` step says so, a clean tree included.
- `roleEval -Prole=session` reads each lifecycle step of the brief against cases of known report, and `testsFor` names a
  role's eval whenever its shipped brief, cases or eval cases change.
