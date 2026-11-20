## ADDED Requirements

### Requirement: A module is reached only through its exposed packages
`project.modules` SHALL map each Gradle module's directory to its `exposes` list of package patterns, `..` for every package.
A declared fitness function with `measure: module-isolation` SHALL count each reference from one declared module to another's type
outside its exposed packages. Each item names both modules and both types. A class in no declared module is not checked.
#### Scenario: A reference outside the exposed packages
- **WHEN** a class of one module references a type of another module whose package that module does not expose
- **THEN** the measurement holds an item naming both modules and both types
#### Scenario: A module exposing every package
- **WHEN** the referenced module exposes `..`
- **THEN** no item is counted for it
#### Scenario: A module without an exposes list
- **WHEN** a module under `project.modules` holds no `exposes`
- **THEN** loading fails with a `FitnessConfigException` naming the module
