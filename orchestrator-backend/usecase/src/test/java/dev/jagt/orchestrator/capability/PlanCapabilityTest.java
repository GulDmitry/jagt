package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.service.PlanApproval;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
