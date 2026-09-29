package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.IntakeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Intake starts work with nobody watching, so a half-written one is refused rather than left to poll for an item
 * it can never recognise. Every stage name is the install's own: a blank one matches nothing, and jagt guessing
 * one would start work nobody asked for.
 */
@Component
@RequiredArgsConstructor
public class IntakeCheck implements StartupCheck {

    private final ConfigService configService;
    private final TrackerWorkflow workflow;

    @Override
    public List<String> problems() {
        IntakeConfig intake = configService.load().intake();
        if (!intake.enabledOrDefault()) {
            return List.of();
        }
        List<String> problems = new ArrayList<>();
        intake.missing().forEach(key -> problems.add("orchestrator.intake." + key
                + ": intake is on and this is blank, so nothing would ever be taken off the tracker"));
        if ("none".equals(workflow.id()) && !intake.missing().contains("tracker")) {
            problems.add("orchestrator.intake.tracker: '" + intake.tracker() + "' is not a workflow jagt"
                    + " carries, so nothing reads your tracker — see jagt.yml.dist for the ones it does");
        }
        return List.copyOf(problems);
    }
}
