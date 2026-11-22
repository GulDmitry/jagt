## MODIFIED Requirements

### Requirement: A macrostep runs microsteps to stability
The engine SHALL take one external event as a macrostep: microsteps of the enabled transitions, then eventless transitions
and the internal queue, until none is enabled, every microstep counted against a ceiling; the step is pure and returns
the configuration, the effects and the transitions it took, in order, as data.

#### Scenario: Order inside a microstep
- **WHEN** transitions are taken
- **THEN** exit actions run innermost first. Transition actions follow in document order, entry actions outermost first
  (IRP 372 375 376 377 378 404 405 406 407 411 412 413)

#### Scenario: An eventless loop is bounded
- **WHEN** eventless transitions keep enabling each other
- **THEN** the macrostep fails with `RaiseCeilingExceeded` naming the moves it took (IRP 396 419 421 423)

#### Scenario: The transitions taken are reported
- **WHEN** a macrostep takes a transition on an event and an eventless one after it
- **THEN** it reports both, in that order, each with its region, source, target and trigger
