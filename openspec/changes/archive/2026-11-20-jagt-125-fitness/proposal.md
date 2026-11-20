## Why
jagt's fitness functions are scattered over test classes, constants, Gradle files and JSON. They become one feature, declared in one file.

## What Changes
- `fitness.yml` declares every automated fitness function: a number, a direction, a place in a reserved tree.
- A loader validates it before any fitness function runs; a project fitness function is a `Fitness` class found by id.
- The report names each fitness function's value, threshold and items; a counted kind keeps its ratchet.
