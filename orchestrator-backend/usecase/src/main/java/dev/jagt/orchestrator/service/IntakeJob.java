package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Work jagt takes off the tracker without being asked. The trigger is the tracker's own stage, checked against
 * the facts read back off the item; nothing here judges what an item MEANS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntakeJob implements Job {

    private final ConfigService configService;
    private final IntakeCandidates candidates;
    private final ProjectRouting routing;
    private final IntakeHistory history;
    private final TaskLauncher launcher;

    @Override
    public String id() {
        return "intake";
    }

    @Override
    public String describe() {
        return "start a task on every item the tracker says is ready for one";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(configService.load().tracker().everyMinutesOrDefault());
    }

    @Override
    public void run() {
        TrackerConfig tracker = configService.load().tracker();
        if (!tracker.modeOrOff().takes() || !tracker.missing().isEmpty()) {
            return;
        }
        // Stamped as the tracker's so the record says who chose each repository, which is what a later
        // routing may learn from and what it must not.
        for (IntakeCandidates.Ready ready : candidates.waiting(history.held()).orElse(List.of())) {
            // One refusal stops the poll: the board, or a router that answered nothing, would give the rest of
            // the list the same answer once per candidate.
            if (!OriginContext.as(ActionOrigin.TRACKER, () -> start(ready))) {
                return;
            }
        }
    }

    private boolean start(IntakeCandidates.Ready ready) {
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
                    new LaunchRequest(item.key(), project.get(), null, null, null, null),
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
