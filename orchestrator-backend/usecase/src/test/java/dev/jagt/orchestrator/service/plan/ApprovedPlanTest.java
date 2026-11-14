package dev.jagt.orchestrator.service.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.repo.UnaffectedRepos;
import org.junit.jupiter.api.Test;

class ApprovedPlanTest {

  private final PlanPlacement placement = mock(PlanPlacement.class);
  private final PlanBaseline baseline = mock(PlanBaseline.class);
  private final UnaffectedRepos unaffected = mock(UnaffectedRepos.class);

  @Test
  void keepsThePlanAsBaselineAndSaysTheTaskWasPlacedWhereItHadNoRepository() {
    when(placement.place("ABC-1")).thenReturn(true);

    boolean placed = new ApprovedPlan(placement, baseline, unaffected).settle("ABC-1");

    assertThat(placed).isTrue();
    verify(baseline).keep("ABC-1");
  }

  @Test
  void keepsThePlanAsBaselineAndSaysNothingWasPlacedWhereTheTaskHeldOneRepository() {
    when(placement.place("ABC-1")).thenReturn(false);

    boolean placed = new ApprovedPlan(placement, baseline, unaffected).settle("ABC-1");

    assertThat(placed).isFalse();
    verify(baseline).keep("ABC-1");
  }
}
