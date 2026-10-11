package dev.jagt.orchestrator.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** What the agent CLI's subscription has used of its windows, selected by {@code orchestrator.agent.cli}. */
public interface SubscriptionUsage {

    Answer read();

    /** Exactly one of three: read, a login holding no subscription, or a read that failed. */
    sealed interface Answer {
    }

    record Read(Snapshot snapshot) implements Answer {
    }

    record NoSubscription() implements Answer {
    }

    /** {@code cause} names what stopped the read and never carries a credential. */
    record Unreadable(String cause) implements Answer {
    }

    record Snapshot(List<Window> windows, Optional<Paid> paid) {
    }

    /** {@code name} is {@code 5h}, {@code week} or {@code week-<model>}; a window may carry no reset yet. */
    record Window(String name, double percentUsed, Optional<Instant> resetsAt) {
    }

    /**
     * What is billed once a window runs out. A vendor reporting only a prepaid {@code balance} answers no
     * {@code used} or {@code monthlyLimit}.
     */
    record Paid(Optional<BigDecimal> used, Optional<BigDecimal> monthlyLimit, String currency,
                Optional<BigDecimal> balance) {
    }
}
