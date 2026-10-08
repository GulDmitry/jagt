package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.service.ProjectRouting;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/** One tracker item placed in a project and started, or turned away; and the keys intake holds already. */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntakeStart {

    private final ProjectRouting routing;
    private final IntakeHistory history;
    private final TaskLauncher launcher;

    public Set<String> held() {
        return history.held();
    }

    /** False where the poll should stop: the rest of the list would get the same answer once per candidate. */
    public boolean start(IntakeCandidates.Ready ready) {
        TicketFacts item = ready.item();
        ProjectRouting.Placement placement = routing.projectFor(item);
        // Not turned away: the next poll asks again, where a turned-away key would wait for a restart.
        if (placement instanceof ProjectRouting.Unreadable) {
            return false;
        }
        Optional<String> project = placement.project();
        if (project.isEmpty()) {
            history.turnAway(item.key(), placement.reason());
            return true;
        }
        try {
            Launched launched = launcher.launch(
                    LaunchRequest.of(item.key()).withProject(project.get()),
                    new Answer<>(Optional.of(item), ready.paid()));
            if (!launched.created()) {
                history.turnAway(item.key(), launched.message());
                return true;
            }
            log.atInfo().setMessage("intake started a task")
                    .addKeyValue("task", item.key())
                    .addKeyValue("project", project.get())
                    .addKeyValue("stage", item.trackerStatus())
                    .log();
            return true;
        } catch (RuntimeException refused) {
            log.atWarn().setMessage("intake could not start a task")
                    .addKeyValue("ref", item.key())
                    .addKeyValue("cause", refused.getMessage())
                    .log();
            return false;
        }
    }
}
