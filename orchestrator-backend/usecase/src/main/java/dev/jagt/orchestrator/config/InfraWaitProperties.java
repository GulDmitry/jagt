package dev.jagt.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** How long, and how often, jagt waits out a failure that is not the code's. An empty list waits for nothing. */
@ConfigurationProperties(prefix = "orchestrator.infra-wait")
public record InfraWaitProperties(List<Duration> backoff) {

    private static final List<Duration> DEFAULT_BACKOFF = List.of(Duration.ofMinutes(5), Duration.ofMinutes(15),
            Duration.ofMinutes(45));

    public InfraWaitProperties {
        backoff = backoff == null ? DEFAULT_BACKOFF : List.copyOf(backoff);
    }

    public static InfraWaitProperties defaults() {
        return new InfraWaitProperties(DEFAULT_BACKOFF);
    }

    public boolean waits() {
        return !backoff.isEmpty();
    }

    /** The wait before attempt {@code attempt}, counted from 0; empty past the last. */
    public Optional<Duration> before(int attempt) {
        return attempt >= 0 && attempt < backoff.size() ? Optional.of(backoff.get(attempt)) : Optional.empty();
    }
}
