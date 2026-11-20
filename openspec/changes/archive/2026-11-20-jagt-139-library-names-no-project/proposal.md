## Why
The fitness library took jagt's `runMinutes` property, a Spring log level and Spring annotations as its own, and its self-check could not see them.
The fitness modules were exempt from the counted kinds, and two ratchets accepted breaches of the rules they guard.

## What Changes
- The minutes property, the contexts log properties and the cache pattern are `fitness { }` keys; the Spring rows move to jagt's `fitness.yml`.
- The library's self-check also scans for the framework and the property names.
- A scan row may leave files out with `except`.
- The fitness modules count under `gradle-modules`, `build-logic-files` and `dependency-lines-in-build-gradle`.
- `home-paths` and `cited-line-numbers` end at 0.
