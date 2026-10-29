## Why

A stop counted on a request the host deleted stood for ever: the read of it was refused, and a refusal ended nothing.

## What Changes

- A read says the request no longer exists with its own refusal kind, apart from one that could not be read.
- That refusal ends a stop that ends on a read; an unreadable one keeps it.

## Capabilities

### Modified Capabilities

- `infra-wait`: a deleted request ends the stop.
