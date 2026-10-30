## MODIFIED Requirements

### Requirement: Startup checks the cases against the code both ways
jagt SHALL refuse to start on a case naming no brief step, or a Master case naming no verb it presses.
It SHALL refuse a status, `by` or reply kind that is none, and a Master `by` not `master`.
It SHALL refuse a case missing `replies:` or the line saying what it is, and an id written twice.
It SHALL refuse a brief step or Master verb no case names, and a relayed Master step no `by: master` case names.
It SHALL refuse a step jagt can send that the brief holds no section for or no case sends.
It SHALL refuse a file it cannot read. Each refusal names the file and the case or move.

#### Scenario: A step jagt sends with no case
- **WHEN** jagt can send `deploy` and no case names it
- **THEN** jagt does not start and names `session-cases.md` and the step

#### Scenario: A step jagt sends with no brief section
- **WHEN** jagt can send `deploy` and neither brief file holds `## deploy`
- **THEN** jagt does not start and names `session-brief.md` and the step
