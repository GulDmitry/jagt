package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/** Whether the session keeping a log has been idle past its cache lifetime, measured from the turn's end in that log. */
@Component
@RequiredArgsConstructor
public class ColdSessions {

    private final AgentRuntime runtime;
    private final ConfigService configService;

    public boolean isCold(Optional<Path> sessionLog) {
        return sessionLog.isPresent() && runtime.turnEndedAt(sessionLog.get()).stream()
                .anyMatch(at -> runtime.pastCacheLifetime(at, configService.load().agent().cacheLifetimeOrDefault()));
    }
}
