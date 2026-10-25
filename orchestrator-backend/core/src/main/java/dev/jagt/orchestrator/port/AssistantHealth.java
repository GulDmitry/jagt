package dev.jagt.orchestrator.port;

import java.util.Optional;

public interface AssistantHealth {

    /** {@code retryAt} is epoch millis; a past value means the next read is the probe. */
    record Outage(String cause, long retryAt) {
    }

    /** Empty while the assistant answers. */
    Optional<Outage> outage();
}
