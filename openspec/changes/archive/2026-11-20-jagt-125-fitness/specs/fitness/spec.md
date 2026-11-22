## ADDED Requirements

### Requirement: fitness.yml is validated before any fitness function runs
Loading `fitness.yml` SHALL fail with a `FitnessConfigException` naming the key, before any fitness function runs, for an unknown key, a wrong type,
a missing threshold, an unknown fitness function id or an unknown selector. Each cause has its own test.
#### Scenario: An unknown key
- **WHEN** a fitness function holds a key outside the fitness function keys
- **THEN** loading fails with a `FitnessConfigException` naming that key and no fitness function runs
#### Scenario: A wrong type
- **WHEN** a threshold holds text instead of a number
- **THEN** loading fails with a `FitnessConfigException` naming the key
#### Scenario: A missing threshold
- **WHEN** a fitness function holds neither `max` nor `min`
- **THEN** loading fails with a `FitnessConfigException` naming the fitness function
#### Scenario: An unknown fitness function id
- **WHEN** a fitness function names a `fitness` id no `Fitness` class carries
- **THEN** loading fails with a `FitnessConfigException` naming the id
#### Scenario: An unknown selector
- **WHEN** a fitness function holds a selector value outside its engine's grammar
- **THEN** loading fails with a `FitnessConfigException` naming the selector

### Requirement: Every section sits in the reserved tree
`fitness.yml` SHALL hold only `schema`, `extends`, `project` and the reserved tree: the book's four dimensions, then the ISO/IEC 25010
and 25012 characteristic and subcharacteristic names. A reserved section may be empty. Every fitness function sits on a subcharacteristic and
`governs` is one of the closed list.
#### Scenario: A section outside the tree
- **WHEN** `fitness.yml` holds a section no dimension, characteristic or subcharacteristic names
- **THEN** loading fails with a `FitnessConfigException` naming the section
#### Scenario: A governs outside the list
- **WHEN** a fitness function holds a `governs` value outside the closed list
- **THEN** loading fails with a `FitnessConfigException` naming the value
#### Scenario: An empty reserved section
- **WHEN** a subcharacteristic holds no fitness function
- **THEN** loading succeeds and the section stays in the report as empty
#### Scenario: A fitness function off a subcharacteristic
- **WHEN** a fitness function sits on a dimension or a characteristic
- **THEN** loading fails with a `FitnessConfigException` naming the fitness function

### Requirement: A fitness function reports its number against its threshold
Every fitness function SHALL yield a measured number against a threshold with a direction, `max` or `min`; a zero fitness function is `max 0`.
The report SHALL name the id, the value, the threshold with its direction and every item. A counted kind below its stored number fails
asking to lower it ([0176](../../../../docs/decisions/0176-every-kind-has-a-ceiling.md)).
#### Scenario: A fitness function fails
- **WHEN** a `max 0` fitness function measures two items
- **THEN** the report names its id, value 2, `max 0` and both items
#### Scenario: A counted kind falls below its number
- **WHEN** a counted kind measures fewer items than its stored number
- **THEN** it fails asking to lower the number to the measured one

### Requirement: A project fitness function is a Fitness class found by id
A fitness function only jagt's domain can say SHALL be a Java class implementing `Fitness`, found by id through `ServiceLoader`.
The config holds no script. A `Fitness` class sits in the package `<dimension>.<characteristic>.<subcharacteristic>` of its section.
#### Scenario: A fitness function found by id
- **WHEN** a fitness function names `fitness: RingsFitness` and a provider carries that id
- **THEN** the class runs and its items fill the report
#### Scenario: A class in the wrong package
- **WHEN** a `Fitness` class sits in a package other than its section's
- **THEN** the structure check fails naming the class and the section

### Requirement: A missing engine, library or tool fails the load
A fitness function whose engine, library or tool is neither on the classpath nor installed SHALL fail the load with a
`FitnessConfigException` of reason `MISSING_ENGINE` or `MISSING_TOOL` naming the fitness function and what is missing, unless it is `optional`.
#### Scenario: An engine is missing
- **WHEN** a fitness function names an engine no provider carries, or a provider cannot be loaded
- **THEN** loading fails with `MISSING_ENGINE` naming the fitness function and the engine
#### Scenario: A tool is missing
- **WHEN** a fitness function needs a tool that is not installed and is not `optional`
- **THEN** loading fails with `MISSING_TOOL` naming the fitness function and the tool

### Requirement: One task runs every fitness function and nothing else
`./gradlew fitness` SHALL run every fitness function (rows, `Fitness` classes, tool rows), print each one's value and threshold,
and run one block of the reserved tree when given one.
#### Scenario: A block is given
- **WHEN** the task gets one block of the reserved tree
- **THEN** only that block's fitness functions run

### Requirement: The fitness library names no project
No file of the library SHALL name a project package, a ring, a path or a vendor, and the library SHALL build alone.
#### Scenario: A project name enters the library
- **WHEN** a library source names `dev.jagt`, a ring, a user path or a vendor
- **THEN** the library's own self-check fails naming the file
