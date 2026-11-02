## MODIFIED Requirements

### Requirement: The Master judges by a brief
The Master SHALL judge by `roles.master.brief`, else the first that exists of `roles/master/brief.md` and
`roles/master/brief.md.dist` (`MasterBriefs.file`); a named brief missing SHALL refuse the start.

#### Scenario: No brief copied
- **WHEN** no brief is copied
- **THEN** the shipped one judges
