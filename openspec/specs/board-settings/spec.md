# board-settings Specification

## Purpose
The board's settings dialog behind the gear: which sections it has, how a value is chosen and marked, and what the browser keeps.

## Requirements

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

### Requirement: The jagt.yml settings dialog keeps one size
The `jagt.yml` settings dialog SHALL have one size for every section, fitted to the viewport. A section longer than it scrolls in
the dialog body; the section tabs and Close stay in view.
#### Scenario: Another section opens
- **WHEN** the human switches from Projects to Review and deploy
- **THEN** the dialog's width and height and the section tabs' position stay the same
#### Scenario: A long section
- **WHEN** a section is longer than the dialog
- **THEN** its body scrolls and the tabs and Close stay in view

### Requirement: A saved setting leaves the dialog as it was
The `jagt.yml` settings dialog SHALL keep its size and every field's position while a setting is saved: the restart footer holds its
height when empty. A row's hint always takes one line: `default` while `jagt.yml` leaves the key out, `set` once it has a value.
The row's hint repaints when a save succeeds.
An unset field shows the default the server sends as its placeholder.
#### Scenario: A restart-bound setting is saved
- **WHEN** the human changes a setting that needs a restart
- **THEN** the dialog's size and every field's position stay the same
- **AND** the row's hint reads `set`
