package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.IntakeConfig;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        return Duration.ofMinutes(configService.load().intake().everyMinutesOrDefault());
    }

    @Override
    public void run() {
        IntakeConfig intake = configService.load().intake();
        if (!intake.enabledOrDefault() || !intake.missing().isEmpty()) {
            return;
        }
        for (IntakeCandidates.Ready ready : candidates.waiting(history.held()).orElse(List.of())) {
            // One refusal stops the poll: what refuses a launch is the board rather than the item, and the
            // rest of the list would buy the same answer once per candidate.
            if (!start(ready)) {
                return;
            }
        }
    }

    private boolean start(IntakeCandidates.Ready ready) {
        TicketFacts item = ready.item();
        List<String> projects = TaskLauncher.projectsMatching(item, labelsByProject());
        if (projects.size() != 1) {
            history.turnAway(item.key(), projects.isEmpty()
                    ? "nothing routes its labels " + item.labels() + " or its tracker project '"
                            + item.trackerProject() + "' to a configured project"
                    : "it routes to several projects " + projects);
            return true;
        }
        try {
            Launched launched = launcher.launch(
                    new LaunchRequest(item.key(), projects.get(0), null, null, null, null),
                    new Answer<>(Optional.of(item), ready.paid()));
            if (!launched.created()) {
                history.turnAway(item.key(), launched.message());
                return true;
            }
            log.atInfo().setMessage("intake started a task")
                    .addKeyValue("task", item.key())
                    .addKeyValue("project", projects.get(0))
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

    private Map<String, List<String>> labelsByProject() {
        Map<String, List<String>> labels = new LinkedHashMap<>();
        configService.load().projects().forEach((key, project) -> labels.put(key, project.labels()));
        return labels;
    }
}
