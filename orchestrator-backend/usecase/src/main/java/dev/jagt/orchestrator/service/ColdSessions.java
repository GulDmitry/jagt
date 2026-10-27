package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;

/** Whether a session standing at a prompt has been idle past its cache lifetime, measured from its turn's end. */
@Component
@RequiredArgsConstructor
public class ColdSessions {

    private final SessionProbe probe;
    private final AgentRuntime runtime;
    private final ConfigService configService;

    public boolean isCold(String taskId) {
        OptionalLong since = probe.atAPromptSince(taskId);
        return since.isPresent() && runtime.pastCacheLifetime(since.getAsLong(),
                configService.load().agent().cacheLifetimeOrDefault());
    }
}
