## ADDED Requirements

### Requirement: Every mark class has a design line and a legend sample
A class of `app.css` carrying a semantic colour, a dashed or dotted line or a glyph SHALL be named in `docs/rules/design.md`.
It SHALL also have a sample in the Help legend. Every selector `design.md` names SHALL exist in `app.css`.
#### Scenario: A mark class without a line or a sample
- **WHEN** a class with a colour, border or glyph is added to `app.css` with no `design.md` line or legend sample
- **THEN** `DesignMarksTest` fails naming the class
#### Scenario: A selector design.md names is gone
- **WHEN** `design.md` names a selector `app.css` no longer holds
- **THEN** `DesignMarksTest` fails naming the selector
