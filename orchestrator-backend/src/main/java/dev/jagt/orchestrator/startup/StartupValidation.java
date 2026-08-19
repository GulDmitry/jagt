package dev.jagt.orchestrator.startup;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Asks every part of the installation whether it is there, and stops the start when one is not.
 *
 * <p>Before the operator surfaces open, so a human never drives a jagt whose next click reaches a binary
 * nobody installed — and ALL problems at once, because starting again to be told the second one is how a
 * five-minute setup takes an hour.
 *
 * <p>What every check asks about is the MACHINE jagt was installed on, which a suite that boots the
 * application is not: a runner has no editor, no desktop terminal and no repositories of the human's. Hence
 * the switch — a harness turns the checks off exactly as it turns off opening a window.
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
