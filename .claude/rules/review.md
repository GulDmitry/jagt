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

Full rules for review rounds and unattended work: **[`openspec/specs/review/spec.md`](../../openspec/specs/review/spec.md)** —
read it before changing behaviour here.
