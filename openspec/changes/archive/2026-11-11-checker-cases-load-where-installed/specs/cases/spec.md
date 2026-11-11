## MODIFIED Requirements

### Requirement: Startup checks the cases against the code both ways
jagt SHALL refuse to start on a case naming no brief step, or a Master case naming no verb it presses.
It SHALL refuse a status, `by` or reply kind that is none, and a Master `by` not `master`.
It SHALL refuse a case missing `replies:` or the line saying what it is, and an id written twice.
A case whose `by` names a checker jagt ships but this setup has not installed SHALL NOT load, nor its step be required;
a `by` naming no shipped checker SHALL be refused.
#### Scenario: A case naming a missing step
- **WHEN** a case says `step: woke` and the brief has no `## woke`
- **THEN** jagt does not start and names `roles/session/cases.md` and that case
#### Scenario: A case naming no replies
- **WHEN** the `wake` case has no `replies:` line, or one naming the kind `maybe`
- **THEN** jagt does not start and names `roles/session/cases.md` and that case
#### Scenario: A revert
- **WHEN** a case names `move: revert`
- **THEN** jagt does not start: the Master never reverts
#### Scenario: A relayed step in the Master's cases
- **WHEN** a Master case names `move: answered`
- **THEN** jagt does not start and names `roles/master/cases.md` and that case
#### Scenario: A checker not installed
- **WHEN** a context lacks `roles/acceptance` and `acceptance-round-by-acceptance` says `by: acceptance`
- **THEN** jagt starts, that case is not loaded and the step `acceptance-round` is not required
#### Scenario: A checker nobody ships
- **WHEN** a case says `by: acceptnce`
- **THEN** jagt does not start and names `roles/session/cases.md` and that case

### Requirement: Startup refuses a step no case names
It SHALL refuse a brief step or Master verb no case names, and a relayed Master step no `by: master` case names.
It SHALL refuse a step jagt can send that the brief holds no section for or no case sends.
It SHALL refuse a file it cannot read. Each refusal names the file and the case or move.
#### Scenario: A step jagt sends with no case
- **WHEN** jagt can send `deploy` and no case names it
- **THEN** jagt does not start and names `roles/session/cases.md` and the step
#### Scenario: A step jagt sends with no brief section
- **WHEN** jagt can send `deploy` and neither brief file holds `## deploy`
- **THEN** jagt does not start and names `roles/session/brief.md` and the step
#### Scenario: A brief step no case sends
- **WHEN** the brief has `## deploy` and no case names it
- **THEN** jagt does not start and names the step
#### Scenario: A file that cannot be read
- **WHEN** your copy of `roles/session/cases.md` exists and cannot be read
- **THEN** jagt does not start and names the file and the cause, and the `.dist` does not stand in for it
#### Scenario: A move no case names
- **WHEN** no Master case names `add-repo`
- **THEN** jagt does not start and names `roles/master/cases.md` and `add-repo`
#### Scenario: A relayed step no session case sets off by the Master
- **WHEN** no session case names `answered-under` with `by: master`
- **THEN** jagt does not start and names `roles/session/cases.md` and `answered-under`
