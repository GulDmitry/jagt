package dev.jagt.orchestrator.surface.board.auth.attempts;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "orchestrator.auth.backoff")
public record LoginBackoff(
    @DefaultValue("5") int failures,
    @DefaultValue("5m") Duration duration,
    @DefaultValue("10000") int peers) {}
