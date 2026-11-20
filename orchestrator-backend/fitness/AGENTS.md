# fitness — functions

Fitness functions declared in one `fitness.yml`, evaluated by engines, run as JUnit dynamic tests. An included build,
meant to leave as its own library and plugin; its decisions live in [`decisions/`](decisions/README.md).

## Boundary

- **Nothing here names a project**: no package, ring, path, vendor or ticket of the project using it. Its selectors live in the
  project's `fitness.yml`, its domain functions in the project's `Fitness` classes.
- **No new library** beyond snakeyaml, ArchUnit, PMD and JUnit.
- **Groovy DSL only**, versions in `gradle.properties`, every configuration locked.

## Modules

| module | holds |
|--------|-------|
| `core` | `FitnessFunction`, `Fitness`, `Engine`, `Report`, `Evaluation`, `Block`, `FitnessRun`, the loader, the reserved tree, `SourceRoots`, `FitnessConfigException` |
| `engine-archunit` | selects classes, fields, methods and calls of the compiled source sets: kind, interface, static, mutable, annotated, assignableTo, calls, without, except, package, set |
| `engine-scan` | selects files (or every tracked text file), directories, lines and matches under a root; measures lines, words, complexity, duplication, assertionless tests; `requires` lists directories lacking a file |
| `junit` | `FitnessTests.from(path)`: one `dynamicContainer` per section, `-Pfitness.only=<id>` |

## FitnessFunctions

- **Every function is a number with a direction**; a `governs: count` function above zero is a ratchet: below its number fails.
- **A failure is a `FitnessConfigException` with a `Reason` and the key**, thrown before any function runs.
- **A `Fitness` class sits in the package of its section**, hyphens dropped.
- **An engine declares its selector vocabulary**; a key or value outside it fails validation.
