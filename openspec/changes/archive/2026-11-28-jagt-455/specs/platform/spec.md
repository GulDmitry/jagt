## ADDED Requirements

### Requirement: The kitty viewer's settings sit in its own block
The kitty viewer SHALL run `kitty.command`, unset `kitty`, and pass `kitty.fontSize` as its font size; unset leaves kitty's own.

#### Scenario: The block is written
- **WHEN** `jagt.yml` sets `kitty.command: /opt/kitty` and `kitty.fontSize: 15`
- **THEN** the viewer runs `/opt/kitty` at font size 15
