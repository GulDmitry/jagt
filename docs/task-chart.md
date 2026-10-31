# The task lifecycle chart

```mermaid
stateDiagram-v2
    state lifecycle {
        state "NEW" as lifecycle_NEW
        state "PLAN_PENDING" as lifecycle_PLAN_PENDING
        state "IN_PROGRESS" as lifecycle_IN_PROGRESS
        state "VERIFYING" as lifecycle_VERIFYING
        state "REVIEW_PENDING" as lifecycle_REVIEW_PENDING
        state "SHIPPING" as lifecycle_SHIPPING
        state "CI_POLLING" as lifecycle_CI_POLLING
        state "CI_FAILED" as lifecycle_CI_FAILED
        state "REVIEWED" as lifecycle_REVIEWED
        state "APPROVED" as lifecycle_APPROVED
        state "DEPLOY_CONFLICT" as lifecycle_DEPLOY_CONFLICT
        state "DEPLOYED" as lifecycle_DEPLOYED
        state "REVERTED" as lifecycle_REVERTED
        state "DONE" as lifecycle_DONE
        [*] --> lifecycle_NEW
        state "all but NEW, PLAN_PENDING, APPROVED, DEPLOY_CONFLICT, DONE" as lifecycle_from_1
        lifecycle_from_1 --> lifecycle_CI_POLLING : ship OK
        state "all but NEW, PLAN_PENDING, APPROVED, DEPLOY_CONFLICT, DONE" as lifecycle_from_2
        lifecycle_from_2 --> lifecycle_SHIPPING : ship RELAYED
        lifecycle_PLAN_PENDING --> lifecycle_IN_PROGRESS : approve-plan RELAYED
        state "all but PLAN_PENDING, DEPLOY_CONFLICT, DEPLOYED, REVERTED, DONE" as lifecycle_from_4
        lifecycle_from_4 --> lifecycle_IN_PROGRESS : add-repo RELAYED
        state "REVIEW_PENDING, CI_POLLING, CI_FAILED, REVIEWED, APPROVED, DEPLOY_CONFLICT, DEPLOYED" as lifecycle_from_5
        lifecycle_from_5 --> lifecycle_DEPLOYED : deploy OK
        state "REVIEW_PENDING, CI_POLLING, CI_FAILED, REVIEWED, APPROVED, DEPLOY_CONFLICT, DEPLOYED" as lifecycle_from_6
        lifecycle_from_6 --> lifecycle_DEPLOY_CONFLICT : deploy CONFLICT
        state "all but DONE" as lifecycle_from_7
        lifecycle_from_7 --> lifecycle_REVERTED : revert OK
        state "REVIEW_PENDING, CI_POLLING, CI_FAILED, REVIEWED, APPROVED, DEPLOY_CONFLICT, DEPLOYED" as lifecycle_from_8
        lifecycle_from_8 --> lifecycle_DEPLOY_CONFLICT : revert CONFLICT
        state "all but DEPLOY_CONFLICT, DONE" as lifecycle_from_9
        lifecycle_from_9 --> lifecycle_DEPLOYED : revert PARTIAL
        state "all but DONE" as lifecycle_from_10
        lifecycle_from_10 --> lifecycle_DONE : done GONE
        state "NEW, PLAN_PENDING, IN_PROGRESS" as lifecycle_from_11
        lifecycle_from_11 --> lifecycle_PLAN_PENDING : report PLAN_PENDING
        state "all but DEPLOY_CONFLICT, REVERTED, DONE" as lifecycle_from_12
        lifecycle_from_12 --> lifecycle_IN_PROGRESS : report IN_PROGRESS
        lifecycle_DEPLOY_CONFLICT --> lifecycle_DEPLOY_CONFLICT : report IN_PROGRESS
        lifecycle_REVERTED --> lifecycle_REVERTED : report IN_PROGRESS
        state "all but DEPLOY_CONFLICT, REVERTED, DONE" as lifecycle_from_13
        lifecycle_from_13 --> lifecycle_VERIFYING : report REVIEW_PENDING owing verification
        state "all but DEPLOY_CONFLICT, REVERTED, DONE" as lifecycle_from_14
        lifecycle_from_14 --> lifecycle_REVIEW_PENDING : report REVIEW_PENDING
        lifecycle_DEPLOY_CONFLICT --> lifecycle_DEPLOY_CONFLICT : report REVIEW_PENDING
        lifecycle_REVERTED --> lifecycle_REVERTED : report REVIEW_PENDING
        state "all but DEPLOY_CONFLICT, REVERTED, DONE" as lifecycle_from_15
        lifecycle_from_15 --> lifecycle_SHIPPING : report SHIPPING
        lifecycle_DEPLOY_CONFLICT --> lifecycle_DEPLOY_CONFLICT : report SHIPPING
        lifecycle_REVERTED --> lifecycle_REVERTED : report SHIPPING
        state "NEW, IN_PROGRESS, REVIEW_PENDING, SHIPPING, CI_POLLING, CI_FAILED" as lifecycle_from_16
        lifecycle_from_16 --> lifecycle_CI_POLLING : report CI_POLLING
        state "all but DEPLOY_CONFLICT, REVERTED, DONE" as lifecycle_from_17
        lifecycle_from_17 --> lifecycle_CI_FAILED : report CI_FAILED
        lifecycle_DEPLOY_CONFLICT --> lifecycle_DEPLOY_CONFLICT : report CI_FAILED
        lifecycle_REVERTED --> lifecycle_REVERTED : report CI_FAILED
        state "CI_POLLING, CI_FAILED, REVIEWED, APPROVED" as lifecycle_from_18
        lifecycle_from_18 --> lifecycle_CI_FAILED : read CI_FAILED
        state "CI_POLLING, CI_FAILED, REVIEWED, APPROVED" as lifecycle_from_19
        lifecycle_from_19 --> lifecycle_REVIEWED : read REVIEWED
        state "CI_POLLING, CI_FAILED, REVIEWED, APPROVED" as lifecycle_from_20
        lifecycle_from_20 --> lifecycle_APPROVED : read APPROVED
        lifecycle_DEPLOYED --> lifecycle_DEPLOY_CONFLICT : deploy read red
        lifecycle_IN_PROGRESS --> lifecycle_DEPLOYED : qa round declined
        lifecycle_VERIFYING --> lifecycle_REVIEW_PENDING : verified
        lifecycle_VERIFYING --> lifecycle_IN_PROGRESS : verification failed
        lifecycle_DONE --> [*]
    }
```
