## MODIFIED Requirements

### Requirement: Startup checks the cases against the code both ways
jagt SHALL refuse to start on a case naming no brief step, or a Master case naming no verb it presses.
It SHALL refuse a status, `by` or reply kind that is none, and a Master `by` not `master`.
It SHALL refuse a case missing `replies:` or the line saying what it is, and an id written twice.
A case whose `by` names a checker jagt ships but this setup has not installed SHALL NOT load, nor its step be required;
a `by` naming neither a shipped nor an installed checker SHALL be refused.
A session case's `by` MAY name any installed checker, which SHALL have an `acceptance-round` case `by: <id>`, or start is refused.
#### Scenario: A case naming a missing step
- **WHEN** a case says `step: woke` and the brief has no `## woke`
- **THEN** jagt does not start and names `roles/session/cases.md` and that case
#### Scenario: A case naming no replies
- **WHEN** the `wake` case has no `replies:` line, or one naming the kind `maybe`
- **THEN** jagt does not start and names `roles/session/cases.md` and that case
#### Scenario: A revert
- **WHEN** a case names `move: revert` **THEN** jagt does not start: the Master never reverts
#### Scenario: A relayed step in the Master's cases
- **WHEN** a Master case names `move: answered`
- **THEN** jagt does not start and names `roles/master/cases.md` and that case
#### Scenario: A checker not installed
- **WHEN** no `roles/acceptance` is installed and a case says `by: acceptance` **THEN** it is not loaded, its step not required
#### Scenario: A checker nobody ships
- **WHEN** a case says `by: acceptnce` **THEN** jagt does not start and names `roles/session/cases.md` and that case
#### Scenario: An installed second checker's round is sent
- **WHEN** audit is installed, a case `by: audit` sends the round and audit finds a row not met **THEN** it is sent, no miss
#### Scenario: An installed checker with no case
- **WHEN** audit is installed and no case sends `acceptance-round` by it **THEN** jagt does not start, naming `roles/session/cases.md`