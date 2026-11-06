## ADDED Requirements

### Requirement: Labels and human paths are typed in the form
The form SHALL carry `labels` and `humanPaths`, each typed as a comma-separated list, blanks dropped; an empty field
writes no key. The edit form shows what `jagt.yml` holds for both.

#### Scenario: Labels and human paths typed
- **WHEN** the human adds `billing` with labels `bill, pay` and human paths `**/auth/**`
- **THEN** `billing`'s block holds `labels: ["bill","pay"]` and `humanPaths: ["**/auth/**"]`

## MODIFIED Requirements

### Requirement: A project is edited and removed
A project's block in `jagt.yml` SHALL be rewritten or deleted as lines (`path`, `baseBranch`, `deployBranch`,
`verifyCommand`, `about`, `labels`, `humanPaths` are rewritten; its other keys, its comments and every other line
stay). The file is read back and put back when it no longer reads. A project an open task works in, and the only
project, are not removed. A file that cannot be written is refused naming it, and stays as it was.

#### Scenario: Editing one project
- **WHEN** the human changes the deploy branch of `billing`, whose block also holds `codeHost` and a comment
- **THEN** only `billing`'s managed keys change; `codeHost`, the comment and the other projects stay
