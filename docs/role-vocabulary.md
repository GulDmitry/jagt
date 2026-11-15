# What a role may hook into and answer

A `role.yml` is compiled by `config/role/RoleDefinitions` from a closed vocabulary; a word outside it is refused.
`RoleVocabularyTest` fails when a word has no row here.

## Triggers

`trigger:` lists when the role's read opens; `deployed` is required.

| trigger | holds when |
|---------|------------|
| `deployed` | the change is live; without it a check would open before the deploy |
| `pipelines-green` | always: the check's key is the digest the green deploy pipelines set |
| `environment-named` | `environments` names one |

## Once per

| `oncePer` | meaning |
|-----------|---------|
| `deploy` | one read per deploy; fixed, declared for the lint only |

## Read steps

`readSteps:` lists the brief steps whose turn opens the role's read: in order the check, its run again after a restart,
and its decline. A role naming fewer repeats its last; one naming three or more runs a decline job.

## Outcomes

`outcomes:` lists what the role may answer; at least one is required. Declared for the lint only.

| outcome | the role answers |
|---------|------------------|
| `round` | the change does not meet a criterion: back to the session |
| `question` | what only a human can answer |
| `wait` | what must change before a criterion can be checked |
| `dispute` | it disagrees with a criterion or a verdict |
| `stop` | it cannot go on |
| `label` | a mark on the task |
