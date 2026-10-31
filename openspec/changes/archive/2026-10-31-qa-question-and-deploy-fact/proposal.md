## Why

A run told "ABC-42 is deployed at <sha>" took landing as given, against a host that reports no deploy job; and a `question` was raised behind rows it had already proved.

## What Changes

- A run is told what jagt knows: the deploy was pushed at the sha. That it landed is QA's to prove.
- A `question` stands only behind an `unprovable` row; a met or not met row asks nothing.

## Capabilities

### Modified Capabilities

- `qa`: what a run is told of the deploy, and when a question may stand.
