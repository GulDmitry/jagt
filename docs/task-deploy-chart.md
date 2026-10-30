# The task deploy chart

```mermaid
stateDiagram-v2
    state deploy {
        state "NONE" as deploy_NONE
        state "LANDING" as deploy_LANDING
        state "HANDED" as deploy_HANDED
        state "REVERTING" as deploy_REVERTING
        state "CHECK_ASKED" as deploy_CHECK_ASKED
        [*] --> deploy_NONE
        deploy_NONE --> deploy_LANDING : opened
        deploy_LANDING --> deploy_LANDING : opened
        deploy_HANDED --> deploy_LANDING : opened
        deploy_REVERTING --> deploy_LANDING : opened
        deploy_CHECK_ASKED --> deploy_LANDING : opened
        deploy_LANDING --> deploy_LANDING : landed
        deploy_LANDING --> deploy_HANDED : conflicted
        deploy_HANDED --> deploy_LANDING : landed
        deploy_HANDED --> deploy_HANDED : conflicted
        deploy_NONE --> deploy_REVERTING : reverted
        deploy_LANDING --> deploy_REVERTING : reverted
        deploy_HANDED --> deploy_REVERTING : reverted
        deploy_REVERTING --> deploy_REVERTING : reverted
        deploy_CHECK_ASKED --> deploy_REVERTING : reverted
        deploy_HANDED --> deploy_LANDING : discarded
        deploy_HANDED --> deploy_HANDED : watched
        deploy_HANDED --> deploy_LANDING : conflict left
        deploy_LANDING --> deploy_CHECK_ASKED : asked
        deploy_LANDING --> deploy_LANDING : settled bare
        deploy_LANDING --> deploy_LANDING : read
        deploy_CHECK_ASKED --> deploy_CHECK_ASKED : settled bare
        deploy_CHECK_ASKED --> deploy_CHECK_ASKED : read
    }
```
