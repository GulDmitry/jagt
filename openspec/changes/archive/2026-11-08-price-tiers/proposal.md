## Why

A model priced in two rate cards by prompt length was booked at its cheaper card for every call, and each row hid its
cache-write rates in global multipliers with no date checked.

## What Changes

- Every price row carries all five rates and the date checked; a row with a long-context rate charges it above its threshold.

## Impact

- Spec: `unpriced-cost`.
- `ClaudePrices`, `ClaudePricesTest`, `docs/cases-h-m.md`.
