## ADDED Requirements

### Requirement: A request belongs to the repository its address names
A request URL SHALL belong to the project whose remote is on the URL's host. Its path MUST be the URL's leading path segments, whole
segments only. A project with no remote is passed over.

#### Scenario: A path that only starts the same
- **WHEN** `group/api` and `group/api-shop` are two projects and the request is `https://host.test/group/api-shop/-/merge_requests/4`
- **THEN** it belongs to `group/api-shop`
