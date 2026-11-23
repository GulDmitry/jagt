# Architecture principles

[← README](../README.md)

Eight rules shape jagt. Each holds on a desktop and on a server. Each has a file or a decision that keeps it true.

## What jagt is and is not

jagt is:
- an orchestrator of AI coding-agent CLI sessions: one ticket, one session, one Git worktree;
- one board in the browser from which a human, or the Master session in `act`, drives every task;
- a statechart that decides every status, on a desktop and on a server alike;
- autonomous by default: the Master session in `act` drives every task, and the human takes over where they choose;
- agnostic: every agent, terminal, OS, tracker and code host is a seam chosen in `jagt.yml`.

jagt is not:
- a coding agent: it launches one and reads what comes back;
- a tracker or a CI system: tasks come from the tracker or the human, pipelines run on the code host;
- a queue: tasks are capped at `maxTasks`;
- a production monitor: an alert reaches jagt only as a tracker item;
- free of bounds: the Master picks only legal moves (`FlowRules`) and never writes the base branch;
- a second surface or GUI automation: the board is the one surface, and no keystroke is ever sent.

## 1. Roles are isolated folders

- What it is: a role is one folder with its brief, its cases and a design file. jagt installs a role. It does not code one.
- Why it matters: a new role changes no existing role, and one role can break only itself.
- Held by: [roles/session/design.md](../roles/session/design.md), [roles/master/design.md](../roles/master/design.md),
  [ARCHITECTURE.md](../ARCHITECTURE.md)

## 2. A statechart decides every status

- What it is: a capability does its work and reports an outcome. A Harel statechart with nested and parallel states, history and choices
  says which status that outcome leads to.
- Why it matters: no code path can move a task to a status the chart does not allow.
- Held by: [statechart](../openspec/specs/statechart/spec.md), [task-flow](../openspec/specs/task-flow/spec.md),
  [0110](decisions/0110-the-task-statechart-runs-on-an-engine-of-our-own.md), [ARCHITECTURE.md](../ARCHITECTURE.md)

## 3. A protocol validated at every step

- What it is: at each step a session reports a typed record. jagt checks it against a schema at the door and returns every violation at once.
- Why it matters: jagt never parses prose, so a changed sentence cannot change a decision.
- Held by: [protocol](../openspec/specs/protocol/spec.md), [0077](decisions/0077-the-masters-verdict-stays-typed.md)

## 4. Deterministic beats a model

- What it is: triggers, guards and legal moves belong to the machine. A model supplies judgement only.
- Why it matters: the Master in act mode picks one legal move and cannot invent one, so an unattended run stays inside the rules.
- Held by: [master](../openspec/specs/master/spec.md), [AGENTS.md](../AGENTS.md)

## 5. Pluggable agents and models

- What it is: a session runs on Claude Code, Codex or OpenCode, on the model you name. A local model can answer the reads that need no tool.
  Each is a key in jagt.yml, like every third party: one port, one driver for each vendor.
- Why it matters: jagt guesses no vendor from a name or a URL, and swapping an agent or a model is a config change.
- Held by: [agent-seams](../openspec/specs/agent-seams/spec.md), [0130](decisions/0130-every-third-party-is-a-configured-seam.md),
  [seams](rules/seams.md)

## 6. What a session does is read, not asked

- What it is: jagt learns what a session does from its CLI's hooks and logs. No model sits in that path.
- Why it matters: the state of a session costs no tokens and cannot be a model's guess.
- Held by: [runtime](../openspec/specs/runtime/spec.md), [0115](decisions/0115-a-session-is-one-stored-record-read-from-typed-signs.md)

## 7. Fitness functions guard the architecture

- What it is: every architecture rule is a fitness function declared in fitness.yml. Each one reports its number against its threshold,
  over all code, tests included. The core imports no Spring, and arrows point inward only.
- Why it matters: a rule that a build checks does not erode when the code grows.
- Held by: [fitness](../openspec/specs/fitness/spec.md), [fitness.yml](../fitness.yml), [0162](decisions/0162-a-check-holds-for-all-code.md),
  [0180](decisions/0180-each-spheres-industry-standard-is-jagts-floor.md)

## 8. Use cases first

- What it is: every behaviour is a WHEN and THEN scenario in a spec, and a test pins it.
- Why it matters: a change shows its effect as a scenario before it shows as code.
- Held by: [spec-changes](../openspec/specs/spec-changes/spec.md), [testing](rules/testing.md)
