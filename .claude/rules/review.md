---
paths:
  - "**/service/ReviewSweepService.java"
  - "**/service/AutoReview*.java"
  - "**/task/AutoReviewWatch.java"
  - "**/dev/jagt/orchestrator/job/**"
  - "**/service/ReviewDrafts.java"
  - "**/flow/AgentReport.java"
  - "**/flow/Pipeline.java"
---

The loop only reads and drafts. It never ships, deploys, pushes or posts.

Full rules for review rounds and unattended work: **`docs/rules/review.md`** — read it before changing behaviour here.
