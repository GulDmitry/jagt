## MODIFIED Requirements

### Requirement: The fitness library names no project
No file of the library SHALL name a project package, ring, path, vendor, framework or build property.
The library SHALL build alone.
A value the library needs from the project SHALL be a key of its Gradle extension with a neutral default.
#### Scenario: A project name enters the library
- **WHEN** a library source names `dev.jagt`, a ring, a user path, a framework or a build property of the project
- **THEN** the library's own self-check fails naming the file

## ADDED Requirements

### Requirement: A scan row may leave files out
A scan function with `except` SHALL leave out every file whose path below the root matches one of its comma-separated globs.
#### Scenario: A file matches the except glob
- **WHEN** a tracked file lies under a path the row's `except` names
- **THEN** the file is not scanned and no item names it
