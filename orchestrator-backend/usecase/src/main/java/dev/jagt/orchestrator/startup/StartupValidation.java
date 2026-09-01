package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.StartupCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Asks every part of the installation whether it is there and stops the start when one is not — ALL problems at
 * once. Switchable off: the checks ask about the machine jagt was installed on, which a test runner is not.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.startup-checks", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class StartupValidation {

    private final List<StartupCheck> checks;

    @EventListener(ApplicationStartedEvent.class)
    public void refuseToRunWithoutWhatItNeeds() {
        List<String> problems = checks.stream().flatMap(check -> problemsOf(check).stream()).toList();
        if (!problems.isEmpty()) {
            throw new Misconfigured(problems);
        }
    }

    /** A check that cannot answer is itself a part that is not there, and the start stops for that too. */
    private static List<String> problemsOf(StartupCheck check) {
        try {
            return check.problems();
        } catch (RuntimeException e) {
            return List.of(check.getClass().getSimpleName() + " could not run: " + e);
        }
    }
}
