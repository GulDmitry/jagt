package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ColdPrompts;
import dev.jagt.orchestrator.service.ColdSessions;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A prompt typed into a session whose cache has expired is the same instruction as one jagt relays, so it goes to a
 * fresh session. The human's message is never lost: it is blocked only after it is on disk and the fresh window is
 * launched, and any failure lets it through.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromptGate {

    private static final String SLASH_COMMAND = "/";
    private static final String PASTED_IMAGE = "[Image #";
    private static final String REASON = "This session sat idle past its cache lifetime: your message went to a fresh"
            + " session, which carries the notes and the end of this conversation.";

    private final ColdSessions cold;
    private final ColdPrompts coldPrompts;
    private final AgentRuntime runtime;

    public String answer(String taskId, @Nullable TaskState task, String prompt, Optional<Path> sessionLog) {
        if (task == null || prompt.isBlank() || prompt.startsWith(SLASH_COMMAND) || prompt.contains(PASTED_IMAGE)
                || !cold.isCold(sessionLog)) {
            return "";
        }
        try {
            coldPrompts.relay(taskId, task, prompt);
        } catch (RuntimeException e) {
            log.atWarn().setMessage("cold prompt not relayed").addKeyValue("task", taskId)
                    .addKeyValue("cause", OneLine.clipped(e.toString())).log();
            return "";
        }
        log.atInfo().setMessage("cold prompt sent to a fresh session").addKeyValue("task", taskId).log();
        return runtime.blockedPrompt(REASON);
    }
}
