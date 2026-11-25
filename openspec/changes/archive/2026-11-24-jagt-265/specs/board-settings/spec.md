# board-settings Specification

## Purpose
The board's settings dialog behind the gear: which sections it has, how a value is chosen and marked, and what the browser keeps.

## ADDED Requirements

### Requirement: The gear opens a settings dialog of named sections
One gear at the header's top right SHALL open a modal dialog, a heading per section, names from the language catalogs: Appearance, the
theme as one choice `auto | light | dark`; Language, one choice of the catalogs present; Configuration, the entry opening the
`jagt.yml` settings ([`project-setup`](../project-setup/spec.md)). A chosen value shows its control's checked state and a ✓, never
colour alone. Esc or a click outside closes it, focus returns to the gear. The legend has the gear's row.
#### Scenario: The dialog opens
- **WHEN** the human clicks the gear
- **THEN** a dialog opens with Appearance, Language and Configuration, each under its heading
#### Scenario: Esc closes the dialog
- **WHEN** the dialog is open and the human presses Esc
- **THEN** it closes and focus is on the gear

### Requirement: Theme and language are kept in this browser
`auto` SHALL follow `prefers-color-scheme`; `light` and `dark` force it. Theme and language are kept in this browser alone and
applied before the first paint. English fills any key a catalog lacks; only the board's own UI text is translated.
#### Scenario: Dark and Polish chosen
- **WHEN** the human chooses `dark` and Polish in the dialog and reloads the board
- **THEN** the page is dark with no flash and reads Polish, a key Polish lacks reads English, and the dialog shows both checked
- **AND** choosing `auto` clears the theme
