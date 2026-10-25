package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.port.AssistantHealth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/** What the header says beside the tasks: the unattended jobs and the assistant. */
@Component
@RequiredArgsConstructor
class HeaderState {

    private final Jobs jobs;
    private final AssistantHealth assistant;
    private final Clock clock;

    Jobs.Summary summary() {
        return jobs.summary(clock.millis());
    }

    List<Jobs.Status> statuses() {
        return jobs.statuses(clock.millis());
    }

    Optional<AssistantHealth.Outage> assistantOutage() {
        return assistant.outage();
    }
}
