package dev.jagt.orchestrator.stand.invariant;

import java.util.List;

public final class NoApprovalByJagt implements Invariant {

  public static final String NAME = "no-approval-by-jagt";

  @Override
  public List<Violation> check(RunRecord run) {
    return run.reviews().approvals().stream()
        .map(approval -> new Violation(NAME, approval.at(), approval.attempt()))
        .toList();
  }
}
