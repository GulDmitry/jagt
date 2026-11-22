## Why
The `fitness` task printed no value for a function a suite measures, a cached `test` stayed green over a breach in a file it did not read, and a scanner's failure counted as no findings.

## What Changes
- A function measured inside a suite run is printed with its last measured value, or `no value yet`.
- The `test` task reads every tracked file of the repository as an input.
- A tool that exits with a failure code or writes to its error stream fails the check with a `ToolFailureException`.
