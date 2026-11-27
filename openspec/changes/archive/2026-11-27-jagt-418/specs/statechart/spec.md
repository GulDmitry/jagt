## ADDED Requirements

### Requirement: A chart that declares its moves classifies every state
Where a chart declares its kinds of move, building SHALL refuse a state of a region a kind is addressed to that no edge takes it in and no
declaration ignores or refuses it in. It SHALL also refuse an edge or a declaration in a region the kind is not addressed to, a declaration
beside an edge, one without its why, and an edge naming no kind.

#### Scenario: A state with no class
- **WHEN** a kind of move has no edge and no declaration for a state of a region it is addressed to
- **THEN** building fails with `move Pull has no class in state door.OPEN`

#### Scenario: An edge on a supertype
- **WHEN** an edge names a supertype of several kinds
- **THEN** it takes every kind under it, and `unclassified` names the states, or the kind itself, with no class for an event
