## ADDED Requirements

### Requirement: No mark is told apart by colour alone
Two marks in one Help legend row SHALL differ in shape or glyph.
A control's edge SHALL be at least 3:1 on `--panel` in both themes ([design.md](../../../docs/rules/design.md)).
#### Scenario: The legend in greyscale
- **WHEN** the human opens `Help`
- **THEN** every pair of marks in a row differs in shape, glyph or text
- **AND** a failed checks dot is a square, a passed one a disc, a deployed verb ends in `↻`, a log chip starts with `▸`
#### Scenario: The Drafted tab
- **WHEN** replies were drafted since the human last opened the Drafted tab
- **THEN** the tab carries a `new` pill and no other tab wears a coloured underline
