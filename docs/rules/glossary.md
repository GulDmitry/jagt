# A name from a discipline is that discipline's term

[← AGENTS.md](../../AGENTS.md)

- Where jagt implements a body of knowledge, every type, method, doc, spec, prompt and log key uses its term.
  Each term has one definition, below; jagt never coins a synonym.
- A term enters its table in the commit that first names it in code or docs; a table lists only terms jagt uses.
- A concept its source has no word for is marked *jagt's own* and never reuses a source's word with another meaning.
- Review asks of every new name: has its discipline a word for it already.

## Statecharts

Sources: Harel, *Statecharts: A Visual Formalism for Complex Systems* (1987); W3C SCXML (2015); OMG UML 2.5.1, state machines.

| term | definition |
|------|------------|
| state | a situation in which the machine waits for an event |
| atomic state | a state with no child states |
| compound state | a state with child states, exactly one of which is active while it is |
| parallel state | a state whose child regions are all active at once |
| orthogonal region | one of a parallel state's independent children |
| initial pseudostate | the default child a compound state enters |
| final state | a state whose entry completes its parent and raises a completion event |
| history pseudostate | re-enters a compound state's last active child (shallow) or descendants (deep) |
| choice pseudostate | a branch whose guards are evaluated when it is reached |
| transition | a move from a source state to a target state, taken on a trigger when its guard holds |
| trigger | the event a transition waits for; a transition without one is eventless |
| guard | a condition that must hold for a transition to be taken |
| action | an effect run on entry, on exit or by a transition |
| do-activity | work that runs for as long as a state is active |
| internal transition | a transition that runs its actions and neither exits nor enters its source |
| configuration | the set of states active at one moment |
| microstep | the execution of one set of enabled transitions |
| macrostep | the microsteps one external event causes, internal events included, until none is enabled |
| internal event | an event raised by an action, processed before the next external event |
| In() predicate | a guard reading whether another state is active |
| data model | the data beside the configuration that guards and actions read |

## Fitness functions

Sources: Ford, Parsons, Kua, Sadalage, *Building Evolutionary Architectures*, 2nd ed. (2022); ISO/IEC 25010, 25012.

| term | definition |
|------|------------|
| fitness function | any mechanism that provides an objective integrity assessment of some architectural characteristic(s) |
| architectural characteristic | a quality the architecture must keep; ISO/IEC 25010 names them as characteristic and subcharacteristic |
| atomic / holistic | the function runs against one context / against a shared context of several |
| triggered / continual / temporal | runs on an event / runs constantly / runs on a schedule |
| static / dynamic | its threshold is fixed / shifts with context |
| automated / manual | a machine runs it / a person does |
| intentional / emergent | defined at the start / found as the system evolves |
| ratchet | *jagt's own*: a counted threshold that only falls ([0176](../decisions/0176-every-kind-has-a-ceiling.md)) |

## Ports and adapters

Source: Cockburn, *Hexagonal Architecture* (2005).

| term | definition |
|------|------------|
| port | an interface the application defines for a conversation with the outside |
| adapter | the code that converts between a port and one technology |
| ring | *jagt's own*: a Gradle module or package set at one distance from the centre ([`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-rings)) |

## Sagas

Source: Garcia-Molina, Salem, *Sagas* (1987).

| term | definition |
|------|------------|
| saga | a long-lived transaction as a sequence of steps, each with a compensating step that undoes it |
