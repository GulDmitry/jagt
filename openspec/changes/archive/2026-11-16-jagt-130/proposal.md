## Why
An operator needs the JVM's and the web server's numbers and the running version, from the running instance alone.

## What Changes
- `/actuator/metrics` and `/actuator/info` answer the logged-in owner; `info` carries the build version from `buildInfo()`.
- The board's title hover names the version; it replaces nothing and adds no mark.
