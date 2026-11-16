## ADDED Requirements

### Requirement: The Code tab lists the change and opens one file beside its source
The Code tab SHALL offer the files the Diff tab reads as a list, each with its added and removed line counts.
It opens one as the file's text before and after the change side by side.
It takes the change from the same read as the Diff tab, loads its library on first use and stores nothing. A cut diff says so.
#### Scenario: A change touching many files
- **WHEN** the human opens Changes in the Code tab of a task that changed two files
- **THEN** both are listed with `+added −removed`
- **WHEN** the human picks one
- **THEN** its old text stands beside its current text, the changed lines marked
