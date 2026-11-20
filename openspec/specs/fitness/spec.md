# fitness Specification

## Purpose
Every automated fitness function of jagt, declared in one `fitness.yml` and measured by the included `fitness` build.

## Requirements

### Requirement: fitness.yml is validated before any fitness function runs
Loading `fitness.yml` SHALL fail before any fitness function runs, with a `FitnessConfigException` naming the key.
The causes are an unknown key, a wrong type, a missing threshold, an unknown fitness function id and an unknown selector.
So is what an engine cannot measure: a missing selector, a `measure` its `per` does not size, a `set` naming no source set,
a `regex` that does not compile.
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
asking to lower it ([0176](../../../docs/decisions/0176-every-kind-has-a-ceiling.md)).
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
A fitness function whose engine, library or tool is neither on the classpath nor installed SHALL fail the load, unless it is `optional`.
The `FitnessConfigException` has reason `MISSING_ENGINE` or `MISSING_TOOL` and names the fitness function and what is missing.
#### Scenario: An engine is missing
- **WHEN** a fitness function names an engine no provider carries, or a provider cannot be loaded
- **THEN** loading fails with `MISSING_ENGINE` naming the fitness function and the engine
#### Scenario: A tool is missing
- **WHEN** a fitness function needs a tool that is not installed and is not `optional`
- **THEN** loading fails with `MISSING_TOOL` naming the fitness function and the tool

### Requirement: One task runs every fitness function and nothing else
`./gradlew fitness` SHALL run every fitness function: rows, `Fitness` classes and tool rows.
It SHALL print each one's value and threshold, and run one block of the reserved tree when given one.
A function a suite run measures SHALL print its threshold and the last value a check measured, or `no value yet`.
#### Scenario: A block is given
- **WHEN** the task gets one block of the reserved tree
- **THEN** only that block's fitness functions run
#### Scenario: A suite-measured function was never measured
- **WHEN** a function is measured by a suite run and no check has measured it
- **THEN** the task prints its threshold and `no value yet`
#### Scenario: A suite-measured function was measured
- **WHEN** a check measured the function at 5
- **THEN** the task prints its threshold and `last value 5`

### Requirement: The fitness library names no project
No file of the library SHALL name a project package, ring, path, vendor, framework or build property.
The library SHALL build alone.
A value the library needs from the project SHALL be a key of its Gradle extension with a neutral default.
#### Scenario: A project name enters the library
- **WHEN** a library source names `dev.jagt`, a ring, a user path, a framework or a build property of the project
- **THEN** the library's own self-check fails naming the file

### Requirement: A scan row may leave files out
A scan function with `except` SHALL leave out every file whose path below the root matches one of its comma-separated globs.
#### Scenario: A file matches the except glob
- **WHEN** a tracked file lies under a path the row's `except` names
- **THEN** the file is not scanned and no item names it

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

### Requirement: A function measuring an empty selection fails
A `max` scan function checking the text, size or presence of the files it selects SHALL fail when the selection holds no file.
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

### Requirement: The test task reads every tracked file
The plugin SHALL declare every tracked file as an input of the `test` task.
A cached run then cannot hide a breach.
#### Scenario: A tracked file changes
- **WHEN** a tracked file changes and nothing else does
- **THEN** the `test` task runs again

### Requirement: A tool that fails fails the check
A tool that exits with a failure code SHALL fail its check; its error stream alone does not, as a scanner logs progress there.
The `ToolFailureException` names the tool, the exit code and the error stream.
The vulnerability scan counts exit code 1 as findings found.
#### Scenario: The tool crashes
- **WHEN** the scanner exits with 127
- **THEN** the check fails with a `ToolFailureException` naming the exit code
#### Scenario: The tool logs to its error stream
- **WHEN** the scanner exits 0 and writes `scanning` to its error stream
- **THEN** the check measures the findings it printed
