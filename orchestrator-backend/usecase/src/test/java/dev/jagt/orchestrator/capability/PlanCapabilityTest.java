package dev.jagt.orchestrator.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.service.PlanApproval;
import dev.jagt.orchestrator.service.Unplaceable;
import dev.jagt.orchestrator.task.AgentReport;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanCapabilityTest {

  private final PlanApproval approval = mock(PlanApproval.class);

  @Test
  void handsTheSessionBackToWorkWhenItWasToldThePlanHolds() {
    when(approval.approve("ABC-1", "The human read plan.md")).thenReturn(true);

    Outcome outcome = new PlanCapability(approval).run("ABC-1");

    assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
  }

  @Test
  void movesNothingWhenTheSessionWasAlreadyToldTheSame() {
    when(approval.approve("ABC-1", "The human read plan.md")).thenReturn(false);

    Outcome outcome = new PlanCapability(approval).run("ABC-1");

    assertThat(outcome.kind()).isEqualTo(Outcome.Kind.NOTHING);
  }

  @Test
  void asksTheHumanToAddWhatThePlanNamesAndJagtCannotPlace() {
    when(approval.approve("ABC-1", "The human read plan.md"))
        .thenThrow(new Unplaceable(List.of("billing"), List.of()));

    Outcome outcome = new PlanCapability(approval).run("ABC-1");

    assertThat(outcome.kind()).isEqualTo(Outcome.Kind.NOTHING);
    assertThat(outcome.stampMeans()).isEqualTo(AgentReport.QUESTION);
    assertThat(outcome.stamp()).contains("billing").contains("add-project form");
    assertThat(outcome.message()).contains("billing").contains("add-project form");
  }
}
