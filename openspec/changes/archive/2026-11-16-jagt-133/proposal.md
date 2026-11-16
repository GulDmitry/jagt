## Why
The web surface had no document naming its assets, actors and ASVS rows (V15).

## What Changes
- `docs/threat-model.md` names assets, actors, boundaries, entry points and STRIDE threats with the ASVS chapters and the code or test holding each.
- `ThreatModelTest` fails when a filter or a route prefix of the web surface is missing from it.
- The engineering spec's threat-model scenario states the closed gap.
