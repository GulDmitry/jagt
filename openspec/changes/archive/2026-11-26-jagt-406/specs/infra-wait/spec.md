## ADDED Requirements

### Requirement: A running pipeline is no wait
A session SHALL report a request's pipeline that is still running as CI_POLLING with the request link, never as `outcome=wait`.

#### Scenario: The pipeline is still running
- **WHEN** ABC-42's session finds its request's pipeline still running
- **THEN** it reports CI_POLLING with the request link, and no wait counts toward the `WAIT` stop
