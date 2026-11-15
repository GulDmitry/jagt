## ADDED Requirements

### Requirement: A start during the stop leaves no session running
With `onStop: end` jagt SHALL record and close sessions only after the web server stopped taking requests.
#### Scenario: a start request arrives while jagt stops
- **WHEN** jagt with `onStop: end` receives SIGTERM and a start request is still being served
- **THEN** that session is also recorded as gone and its window closed before jagt exits
