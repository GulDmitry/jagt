package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.UsageLimitsProperties;
import dev.jagt.orchestrator.port.SubscriptionUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Arms a session's wake at the reset of the usage windows that stopped it. */
@Component
@Slf4j
@RequiredArgsConstructor
public class UsageLimitWakes {

    private final UsageLimits limits;
    private final UsageLimitsProperties properties;
    private final SessionWakes wakes;
    private final Clock clock;

    /** Arms a wake at the reset of every window at its limit; none where no window is, or one carries no reset. */
    public void arm(String taskId) {
        if (!properties.wakes()) {
            return;
        }
        Optional<Instant> at = limits.refresh(clock.instant()).map(UsageLimits.Reading::answer)
                .filter(SubscriptionUsage.Read.class::isInstance).map(SubscriptionUsage.Read.class::cast)
                .flatMap(read -> resetOfExhausted(read.snapshot().windows()));
        if (at.isEmpty()) {
            log.atInfo().setMessage("session wake not armed").addKeyValue("task", taskId)
                    .addKeyValue("cause", "no window at its limit with a known reset").log();
            return;
        }
        wakes.arm(taskId, at.get(), "wake");
    }

    public void cancel(String taskId) {
        wakes.cancel(taskId);
    }

    private static Optional<Instant> resetOfExhausted(List<SubscriptionUsage.Window> windows) {
        List<SubscriptionUsage.Window> exhausted = windows.stream().filter(window -> window.percentUsed() >= 100)
                .toList();
        if (exhausted.isEmpty() || exhausted.stream().anyMatch(window -> window.resetsAt().isEmpty())) {
            return Optional.empty();
        }
        return exhausted.stream().map(window -> window.resetsAt().get()).max(Comparator.naturalOrder());
    }
}
