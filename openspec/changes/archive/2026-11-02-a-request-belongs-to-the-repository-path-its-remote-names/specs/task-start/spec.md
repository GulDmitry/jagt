## MODIFIED Requirements

### Requirement: A request belongs to the repository its address names
A request URL SHALL belong to the project whose remote path is the URL's leading path segments, whole segments only.
The name the remote reaches the host by is no part of it. Where several projects match, the one whose remote is on the URL's host wins.
A project with no remote is passed over.

#### Scenario: A path that only starts the same
- **WHEN** `group/api` and `group/api-shop` are two projects and the request is `https://host.test/group/api-shop/-/merge_requests/4`
- **THEN** it belongs to `group/api-shop`

#### Scenario: A remote reached by an alias
- **WHEN** a project's remote is `git@github-work:group/api.git` and the request is `https://github.com/group/api/pull/4`
- **THEN** it belongs to that project
