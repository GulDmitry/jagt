## Why

The endings ran before the web server stopped taking requests, so a start request in that window left a session running after the stop.

## What Changes

- With `onStop: end` the endings run after the web server stopped taking requests.

## Impact

- `shutdown`: gains "A start during the stop leaves no session running".
