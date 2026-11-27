## MODIFIED Requirements

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
