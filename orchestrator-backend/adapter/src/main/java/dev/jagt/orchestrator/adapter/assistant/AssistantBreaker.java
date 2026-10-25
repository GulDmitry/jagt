package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.AssistantHealth;
import dev.jagt.orchestrator.port.AssistantUnavailable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Stops headless reads after consecutive provider failures, then lets one probe through once the cool-down has
 * passed. A refused read is never queued.
 */
@Component
@Slf4j
class AssistantBreaker implements AssistantHealth {

    private enum Phase {
        CLOSED,
        OPEN,
        /** One read is in flight to learn whether the provider is back. */
        PROBING
    }

    private final int threshold;
    private final Duration cooldown;
    private final Clock clock;
    private Phase phase = Phase.CLOSED;
    private int failures;
    private long retryAt;
    private String cause = "";

    AssistantBreaker(AssistantProperties assistant, Clock clock) {
        this.threshold = assistant.breakerFailures();
        this.cooldown = assistant.breakerCooldown();
        this.clock = clock;
    }

    synchronized void admit() {
        if (phase == Phase.CLOSED) {
            return;
        }
        if (phase == Phase.OPEN && clock.millis() >= retryAt) {
            phase = Phase.PROBING;
            log.atInfo().setMessage("assistant probe started")
                    .addKeyValue("cause", cause)
                    .log();
            return;
        }
        throw new AssistantUnavailable(cause);
    }

    synchronized void succeeded() {
        if (phase != Phase.CLOSED) {
            log.atInfo().setMessage("assistant breaker closed")
                    .addKeyValue("previous", phase.name().toLowerCase(java.util.Locale.ROOT))
                    .addKeyValue("cause", cause)
                    .log();
        }
        phase = Phase.CLOSED;
        failures = 0;
        cause = "";
    }

    synchronized void failed(String why) {
        Phase before = phase;
        cause = why;
        failures++;
        if (before != Phase.PROBING && failures < threshold) {
            return;
        }
        phase = Phase.OPEN;
        retryAt = clock.millis() + cooldown.toMillis();
        log.atWarn().setMessage(before == Phase.PROBING ? "assistant breaker reopened" : "assistant breaker opened")
                .addKeyValue("cause", why)
                .addKeyValue("attempt", failures)
                .addKeyValue("limit", threshold)
                .addKeyValue("effect", "headless reads refused until the next probe")
                .addKeyValue("fix", "orchestrator.assistant.breakerCooldown")
                .log();
    }

    /** An outcome that says nothing about the provider frees a probe slot and leaves the streak alone. */
    synchronized void inconclusive() {
        if (phase == Phase.PROBING) {
            phase = Phase.OPEN;
        }
    }

    @Override
    public synchronized Optional<Outage> outage() {
        return phase == Phase.CLOSED ? Optional.empty() : Optional.of(new Outage(cause, retryAt));
    }
}
