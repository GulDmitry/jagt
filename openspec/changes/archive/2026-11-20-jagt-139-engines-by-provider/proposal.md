## Why
Core named the engines `archunit` and `scan` and their libraries, so a new engine needed a core edit.
A provider that failed to initialise read as a missing engine, with the service interface as its key.
Two engines or two `Fitness` classes with one name failed with an untyped `IllegalStateException`.

## What Changes
- An engine name no provider serves is `MISSING_ENGINE`; core knows engines only through their providers.
- An engine names the libraries it needs and a class to recognise each by.
- A provider that cannot be loaded fails as `PROVIDER_FAILED` carrying the cause.
- Two engines with one name fail as `DUPLICATE_ENGINE`, two `Fitness` classes with one id as `DUPLICATE_ID`.
