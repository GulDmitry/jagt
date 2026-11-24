## ADDED Requirements

### Requirement: An exception to a named bad practice names a decision holding two agreements
A fitness function MAY carry `exception: "<number>"`, the decision granting the exceptions of its `except` list.
That decision's Status SHALL hold a line `Owner approved <date>` and a line `Reviewer agreed <date>, <who>`; so SHALL every
decision whose Status opens a line with `Exception`. An `exception` naming no decision fails the same check.
A named bad practice SHALL be a `max 0` function or a counted ratchet that only falls.
#### Scenario: A decision lacking one agreement
- **WHEN** an exception decision's Status lacks the owner's approval or the reviewer's agreement
- **THEN** `DecisionIndexTest` fails naming the decision
#### Scenario: A first new use of a named practice
- **WHEN** a change adds a `Thread.sleep` to main beyond the ratchet's number
- **THEN** the fitness run fails naming `sleep-in-main`
