## Why

A tracker or code host opens a task with its plan gate closed, but only the brief and the Master's prompt keep it closed:
a session reporting `no_plan` opens it, and the Master's refusal of an unsound plan is a judgement, not a contract.

## What Changes

- The task records the `ActionOrigin` that opened it.
- On a task an outside system opened, `flow/` refuses a `no_plan` report while the gate is closed.
- On such a task `flow/` refuses a Master approval unless `plan.md` opens with `verdict: sound`; the human's approval stays allowed.
- Each refusal is a `Refusal` the session or the Master reads.
