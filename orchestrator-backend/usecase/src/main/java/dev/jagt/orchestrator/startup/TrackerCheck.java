package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.task.TrackerMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Either end the tracker drives runs with nobody watching, so a half-written one is refused rather than left to
 * poll for a stage it can never recognise. Every stage name is the install's own: a blank one matches nothing,
 * and jagt guessing one would start work nobody asked for.
 */
@Component
@RequiredArgsConstructor
public class TrackerCheck implements StartupCheck {

    private final ConfigService configService;
    private final TrackerWorkflow workflow;

    @Override
    public List<String> problems() {
        TrackerConfig tracker = configService.load().tracker();
        if (TrackerMode.of(tracker.mode()).isEmpty()) {
            return List.of("orchestrator.tracker.mode: '" + tracker.mode() + "' is not one of "
                    + TrackerMode.ids());
        }
        if (tracker.modeOrOff() == TrackerMode.OFF) {
            return List.of();
        }
        List<String> problems = new ArrayList<>();
        tracker.missing().forEach(key -> problems.add("orchestrator.tracker." + key + ": mode is '"
                + tracker.modeOrOff().id() + "' and this is blank, so that end would never recognise"
                + " anything"));
        if ("none".equals(workflow.id()) && !tracker.missing().contains("workflow")) {
            problems.add("orchestrator.tracker.workflow: '" + tracker.workflow() + "' is not a workflow jagt"
                    + " carries, so nothing reads your tracker — see jagt.yml.dist for the ones it does");
        }
        return List.copyOf(problems);
    }
}
