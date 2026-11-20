## Why
A scan row without its regex, glob or a sizable measure, a set naming no source set and a regex that does not compile failed only once the run had started.
A row measuring no file passed, `project.sourceSets` was never read, and an empty `basePackage` measured nothing.

## What Changes
- An engine validates each of its functions at load, as a typed `FitnessConfigException`.
- A `max` function checking the text or size of files fails when its selection holds no file.
- `project.sourceSets` bounds every `set`.
- An empty `project.basePackage` means every package.
