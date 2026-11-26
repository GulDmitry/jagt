## ADDED Requirements

### Requirement: The jagt.yml settings dialog keeps one size
The `jagt.yml` settings dialog SHALL have one size for every section, fitted to the viewport. A section longer than it scrolls in
the dialog body; the section tabs and Close stay in view.
#### Scenario: Another section opens
- **WHEN** the human switches from Projects to Review and deploy
- **THEN** the dialog's width and height and the section tabs' position stay the same
#### Scenario: A long section
- **WHEN** a section is longer than the dialog
- **THEN** its body scrolls and the tabs and Close stay in view
