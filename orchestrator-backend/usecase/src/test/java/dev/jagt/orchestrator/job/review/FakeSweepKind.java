package dev.jagt.orchestrator.job.review;

import dev.jagt.orchestrator.service.review.ReviewSweepService.SweepResult;
import java.util.concurrent.atomic.AtomicReference;

public final class FakeSweepKind {

  private final AtomicReference<SweepResult.Kind> kind =
      new AtomicReference<>(SweepResult.Kind.PENDING);

  public SweepResult.Kind kind() {
    return kind.get();
  }

  public void answers(SweepResult.Kind next) {
    kind.set(next);
  }
}
