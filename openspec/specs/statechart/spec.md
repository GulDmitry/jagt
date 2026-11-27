# statechart Specification

## Purpose
The `:statechart` engine's contract: Harel and SCXML semantics as a pure macrostep, pinned by W3C IRP cases.

## Requirements

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

### Requirement: States nest
A compound state SHALL hold child states and enter its initial child through its initial pseudostate; only a leaf is
active alone, its ancestors implied.

#### Scenario: Entering a compound
- **WHEN** a transition targets a compound state
- **THEN** its initial child is entered after the compound's own entry actions (IRP 355 364 576)

### Requirement: Selection prefers the deepest source
Per region the innermost enabled source SHALL win, document order among equals; a transition whose exit set meets a
deeper one's is dropped; an internal transition leaves its source unexited.

#### Scenario: A descendant preempts its ancestor
- **WHEN** a state and its descendant both have a transition on the event
- **THEN** the descendant's is taken (IRP 403a 403b 403c 436 503 504 505 506 533)

### Requirement: Completion raises done.state
Entering a final child SHALL raise `done.state` of its parent; a parallel state whose every region is final SHALL raise
its own; a chart whose every top-level region is final SHALL halt.

#### Scenario: A parallel state completes
- **WHEN** the last of a parallel state's regions enters a final state
- **THEN** its `done.state` is raised once (IRP 144 415 416 417 570)

### Requirement: History restores the last configuration
Shallow history SHALL restore the last active child of its compound, deep history its last leaf, and its default when
nothing is recorded; history persists beside the configuration and an old flat configuration reads as none recorded.

#### Scenario: Re-entering through history
- **WHEN** a compound left from a nested leaf is re-entered through its deep history
- **THEN** that leaf is active again (IRP 387 388)

### Requirement: A choice is decided where it is reached
A choice pseudostate SHALL evaluate its guards after the transition's exits, `otherwise` its default, and never be stable;
a do-activity SHALL start as an entry effect and stop as an exit effect.

#### Scenario: A guard reads a state the transition exits
- **WHEN** a choice's guard reads `In()` of a state its own transition exits
- **THEN** it reads that state as inactive

### Requirement: A chart is validated when built
Building SHALL refuse a choice or a history without a default and a compound whose initial is not its child.
It SHALL refuse an unreachable state and a non-final state with no way out, per level.

#### Scenario: A choice with no default
- **WHEN** a chart declares a choice with no `otherwise`
- **THEN** building fails naming the choice

### Requirement: A chart that declares its moves classifies every state
Building a chart that declares its kinds of move SHALL refuse a state with no class for a kind. A class is an edge, an ignoring or a
refusal, in a region the kind is addressed to. It SHALL also refuse an edge or a declaration in a region the kind is not addressed to.
It SHALL refuse a kind addressed to no region that no role is declared to own.
It SHALL refuse a declaration beside an edge, one without its why, and an edge naming no kind.

#### Scenario: A state with no class
- **WHEN** a kind of move has no edge and no declaration for a state of a region it is addressed to
- **THEN** building fails with `move Pull has no class in state door.OPEN`

#### Scenario: A kind with no region and no owner
- **WHEN** a kind of move is addressed to no region and no role is declared to own it
- **THEN** building fails with `move Tug is addressed to no region and owned by no role`

#### Scenario: An edge on a supertype
- **WHEN** an edge names a supertype of several kinds
- **THEN** it takes every kind under it, and `unclassified` names the states, or the kind itself, with no class for an event
