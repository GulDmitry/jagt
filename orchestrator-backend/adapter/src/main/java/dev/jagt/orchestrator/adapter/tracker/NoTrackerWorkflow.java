package dev.jagt.orchestrator.adapter.tracker;

import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.task.TicketFacts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** An install with no tracker: every stage still has an answer, and the answer is that nothing arrives unasked. */
@Component
@ConditionalOnProperty(name = "orchestrator.intake.tracker", havingValue = "none", matchIfMissing = true)
public class NoTrackerWorkflow implements TrackerWorkflow {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public String candidateQuery() {
        return "";
    }

    @Override
    public boolean startsWork(TicketFacts facts) {
        return false;
    }

    @Override
    public boolean closesWork(TicketFacts facts) {
        return false;
    }
}
