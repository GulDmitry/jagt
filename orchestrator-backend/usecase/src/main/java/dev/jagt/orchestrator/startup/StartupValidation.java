package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Asks every part of the installation whether it is there and stops the start when one is not — ALL problems at
 * once. Switching the checks off skips those about the machine, which a test runner is not; jagt's own files are
 * checked always.
 */
@Component
@RequiredArgsConstructor
public class StartupValidation {

    private final List<StartupCheck> checks;
    @Value("${orchestrator.startup-checks:true}")
    private final boolean machineChecked;

    @EventListener(ApplicationStartedEvent.class)
    public void refuseToRunWithoutWhatItNeeds() {
        List<Problem> problems = checks.stream().filter(check -> machineChecked || !check.machine())
                .flatMap(check -> problemsOf(check).stream()).toList();
        if (!problems.isEmpty()) {
            throw new Misconfigured(problems);
        }
    }

    /** A check that cannot answer is itself a part that is not there, and the start stops for that too. */
    private static List<Problem> problemsOf(StartupCheck check) {
        try {
            return check.problems();
        } catch (RuntimeException e) {
            String name = check.getClass().getSimpleName();
            return List.of(new Problem(name, name + " could not run: " + e));
        }
    }
}
