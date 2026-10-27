package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** Whether a session has been idle past its cache lifetime, measured from its turn's end in the session's own record. */
@Component
@RequiredArgsConstructor
public class ColdSessions {

    private final AgentRuntime runtime;
    private final ConfigService configService;

    public boolean isCold(TaskState task) {
        String worktree = task.worktreePath();
        return worktree != null && !worktree.isBlank() && runtime.turnEndedAt(Path.of(worktree)).stream()
                .anyMatch(at -> runtime.pastCacheLifetime(at, configService.load().agent().cacheLifetimeOrDefault()));
    }
}
