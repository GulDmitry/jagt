## ADDED Requirements

### Requirement: A disagreement offers a lesson, never applies one
The `master` report SHALL offer a `master-lesson` run for the latest task you reverted after a pass or deployed after
a fail (`MasterRecord`); it changes no brief.

#### Scenario: You reverted what it passed
- **WHEN** ABC-42 passed and you reverted it
- **THEN** `master` shows a `lesson:` line: what to paste into `/master-lesson`
