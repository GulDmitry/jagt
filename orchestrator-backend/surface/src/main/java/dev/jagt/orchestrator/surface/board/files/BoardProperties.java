package dev.jagt.orchestrator.surface.board.files;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrator.board")
public record BoardProperties(@Nullable String dir) {}
