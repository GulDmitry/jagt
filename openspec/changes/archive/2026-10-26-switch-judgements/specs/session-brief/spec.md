## ADDED Requirements

### Requirement: A session judges each switch that is on
A session SHALL answer each of `autoplan`, `visualize` and `diagnose` that is on in `jagt.yml` as `yes — <why>` or `no — <why>`
in the `switches` field (`protocol/SwitchJudgements`) of its first `update_agent_status` report, whatever status it reports.
jagt SHALL refuse that report as `validation`, naming the switch and the form, when one is missing, lacks its why or is
unknown; reports after the first need none and a later answer replaces an earlier. The answers SHALL be kept on the task
and shown in the hover of its card and above the documents of its Artifacts tab, as `visualize: no — <why>`.

#### Scenario: A switch left unanswered
- **WHEN** `visualize` is on and the first report of ABC-42 carries no `switches` answer for it
- **THEN** the report is refused as `validation` naming `visualize`, and the task stays NEW

#### Scenario: A switch that is off
- **WHEN** `visualize` is off and the first report of ABC-42 carries no answer for it
- **THEN** the report is accepted

#### Scenario: A no with its why
- **WHEN** a session reports `switches: {"visualize": "no — config-only change"}`
- **THEN** the card's hover and the Artifacts tab of ABC-42 read `visualize: no — config-only change`
