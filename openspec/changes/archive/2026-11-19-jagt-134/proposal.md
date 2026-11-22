## Why
A role is an optional plug-in: with the Master absent the board's settings page shows no Master section.

## What Changes
- A role contributes its own settings through `RoleSettingKeys`; `Settings` appends them to the basic keys.
- The Master's keys are present only where `roles/master` is installed; the page derives its sections from the keys.
