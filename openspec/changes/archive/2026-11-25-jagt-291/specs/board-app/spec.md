## ADDED Requirements

### Requirement: The board sorts in English and asks only catalog keys it holds
No board script SHALL compare, sort or format in the browser's locale: `localeCompare` and `Intl` constructors take a literal locale.
`en.json` SHALL hold every key a script or page asks for, and no other. Each legend row SHALL carry its own key.
#### Scenario: Aliases are compared
- **WHEN** two aliases are compared in a browser of any locale
- **THEN** their order is the English numeric one, `p2` before `p10`

#### Scenario: A key is missing
- **WHEN** a board source asks `t()` or a `data-t*` attribute for a key `en.json` lacks
- **THEN** the board's catalog test fails naming the key
