package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.TaskState;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * A prompt typed into a session whose cache has expired is the same instruction as one jagt relays,
 * so it goes to a fresh session. The human's message is never lost: it is blocked only after it is
 * on disk and the fresh window is launched, and any failure lets it through.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromptGate {

  private static final String SLASH_COMMAND = "/";
  private static final String PASTED_IMAGE = "[Image #";

  private final AgentSessions sessions;
  private final AgentRuntime runtime;
  private final ConfigService configService;

  public String answer(String taskId, @Nullable TaskState task, String prompt) {
    if (task == null
        || prompt.isBlank()
        || prompt.startsWith(SLASH_COMMAND)
        || prompt.contains(PASTED_IMAGE)
        || !cold(task)) {
      return "";
    }
    try {
      sessions.relayUnderCold(taskId, prompt);
    } catch (RuntimeException e) {
      log.atWarn()
          .setMessage("cold prompt not relayed")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", OneLine.clipped(e.toString()))
          .log();
      return "";
    }
    log.atInfo()
        .setMessage("cold prompt sent to a fresh session")
        .addKeyValue("task", taskId)
        .log();
    return runtime.blockedPrompt(
        ("Your message moved to a fresh session of this task, which is working on it now: this one"
                + " stood idle for over %s, so its cache had expired.")
            .formatted(threshold()));
  }

  private String threshold() {
    Duration lifetime = configService.load().agent().cacheLifetimeOrDefault();
    return lifetime.toMinutes() % 60 == 0
        ? lifetime.toHours() + "h"
        : lifetime.toMinutes() + " min";
  }

  private boolean cold(TaskState task) {
    return SessionPhase.cold(
        task.work().session(), runtime, configService.load().agent().cacheLifetimeOrDefault());
  }
}
