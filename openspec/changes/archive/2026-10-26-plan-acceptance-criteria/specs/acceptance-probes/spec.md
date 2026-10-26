## ADDED Requirements

### Requirement: A plan carries its tagged acceptance criteria
`plan.md` SHALL hold a `## Acceptance criteria` section of at least one `- ` item, each `- [here] <text>`,
`- [elsewhere: <part>] <text>` or `- [wrong: <why>] <text>`; `, own` inside the brackets marks one the session wrote itself
(`protocol/PlanCriteria`). The criteria SHALL come from the ticket, read with every field, since a tracker may keep them in a
custom field; where it has none the session writes them. A criterion of a part this task does not build is `elsewhere`.
jagt SHALL refuse a PLAN_PENDING report whose `plan.md` is missing, lacks the section or holds an item without a valid tag, as `validation`.
The Master's plan read SHALL flag a criterion the ticket names that the plan dropped or mis-tagged.

#### Scenario: Criteria in a custom field
- **WHEN** ABC-42 keeps its acceptance criteria in a custom field the tracker's default read omits
- **THEN** the session reads the ticket with every field and the plan lists each criterion, tagged

#### Scenario: A ticket without criteria
- **WHEN** ABC-42 names no acceptance criteria
- **THEN** the plan holds criteria the session wrote, each tagged `, own`

#### Scenario: A criterion for another part
- **WHEN** a criterion of ABC-42 describes the frontend and the task builds the backend
- **THEN** the plan keeps it as `- [elsewhere: frontend] <text>`

#### Scenario: A plan without the section
- **WHEN** a session reports PLAN_PENDING and `plan.md` has no `## Acceptance criteria` section, or no `plan.md` exists
- **THEN** the report is refused as `validation`, the task stays IN_PROGRESS

#### Scenario: An item without a tag
- **WHEN** an item reads `- the list shows the status`
- **THEN** the report is refused as `validation`, naming the line
