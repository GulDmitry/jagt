## MODIFIED Requirements

### Requirement: fitness.yml is validated before any fitness function runs
Loading `fitness.yml` SHALL fail before any fitness function runs, with a `FitnessConfigException` naming the key.
The causes are an unknown key, a wrong type, a missing threshold, an unknown fitness function id and an unknown selector,
and what an engine cannot measure: a missing selector, a `measure` its `per` does not size, a `set` naming no source set, a `regex` that does not compile.
Each cause has its own test.
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
#### Scenario: A scan function lacking what it measures with
- **WHEN** a scan function is `per: line` without `regex`, holds neither `glob` nor `set`, or sizes with a `per` and `measure` that do not pair
- **THEN** loading fails with a `FitnessConfigException` naming the missing or unsupported selector
#### Scenario: A set naming no source set
- **WHEN** a function holds `set: mian` and the project has no such source set
- **THEN** loading fails with a `FitnessConfigException` naming the `set` key
#### Scenario: A regex that does not compile
- **WHEN** a scan function holds `regex: "("`
- **THEN** loading fails with a `FitnessConfigException` naming the `regex` key

## ADDED Requirements

### Requirement: A function measuring an empty selection fails
A scan function with `max` that checks the text, size or presence of the files its `glob` or `set` selects SHALL fail when the selection holds no file.
A function listing the selected files themselves is not checked.
#### Scenario: A size function matching no file
- **WHEN** a function sizing files by `glob` finds none
- **THEN** it fails with an item saying the glob matches no file
#### Scenario: A listing of files
- **WHEN** a `max 0` function lists the files its glob matches and none exists
- **THEN** it passes

### Requirement: project.sourceSets bounds every set
`project.sourceSets` SHALL name the source sets a function's `set` may select, `all` for every source set of every module.
A `set` naming a source set outside them fails the load.
#### Scenario: All source sets
- **WHEN** `project.sourceSets` is `all` and a function holds `set: all`
- **THEN** every source root of every module is selected
#### Scenario: A listed source set
- **WHEN** `project.sourceSets` lists only `main` and a function holds `set: e2e`
- **THEN** loading fails with a `FitnessConfigException` naming the `set` key

### Requirement: An empty base package means every package
An empty `project.basePackage` SHALL prefix no selector of the archunit engine, so its selectors match in every package.
#### Scenario: No base package
- **WHEN** `project.basePackage` is empty and a function holds `package: fitness.archunit..`
- **THEN** the classes of that package are measured
