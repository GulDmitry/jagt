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
  private final PlanVerdict verdict = mock(PlanVerdict.class);

  @Test
  void keepsThePlanAsBaselineAndSaysTheTaskWasPlacedWhereItHadNoRepository() {
    when(placement.place("ABC-1")).thenReturn(true);

    boolean placed = new ApprovedPlan(placement, baseline, unaffected, verdict).settle("ABC-1");

    assertThat(placed).isTrue();
    verify(baseline).keep("ABC-1");
  }

  @Test
  void keepsThePlanAsBaselineAndSaysNothingWasPlacedWhereTheTaskHeldOneRepository() {
    when(placement.place("ABC-1")).thenReturn(false);

    boolean placed = new ApprovedPlan(placement, baseline, unaffected, verdict).settle("ABC-1");

    assertThat(placed).isFalse();
    verify(baseline).keep("ABC-1");
  }

  @Test
  void callsThePlanSoundWhereItsHeadIsRead() {
    when(verdict.sound("ABC-1")).thenReturn(true);

    assertThat(new ApprovedPlan(placement, baseline, unaffected, verdict).sound("ABC-1")).isTrue();
  }

  @Test
  void callsThePlanNotSoundWhereItsHeadIsNotRead() {
    when(verdict.sound("ABC-1")).thenReturn(false);

    assertThat(new ApprovedPlan(placement, baseline, unaffected, verdict).sound("ABC-1")).isFalse();
  }
}
