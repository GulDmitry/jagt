## Why

The task chart's engine named its parts in its own words and lacked compound states, history, completion and eventless
transitions; jagt's core flow runs on it, so it follows Harel, SCXML and UML in semantics and vocabulary.

## What Changes

- The engine speaks statechart: `Statechart`, `Transition`, `Guard`, `Active` (`In()`), `Datum`, `Macrostep`.
- It gains compound and parallel states, entry actions, initial pseudostates, shallow and deep history, completion
  (`done.state`), eventless transitions, choice pseudostates and do-activities, checked by W3C SCXML IRP cases.
- `flow/` keeps its behaviour; its rendered chart changes in its comment line alone.

## Impact

- New capability `statechart`; module `:statechart`; decision 0216 amends 0110.
