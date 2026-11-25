package dev.jagt.orchestrator.service.launch;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.CommandAssistant;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import dev.jagt.orchestrator.task.launch.TaskName;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * The branch of a task nobody filed: a model's few words for what the work does where it gave some,
 * else the human's opening words.
 */
@Service
@RequiredArgsConstructor
public class WrittenTaskNames {

  private final CommandAssistant namer;
  private final UsageTracker usage;

  /** {@code name} is null where nothing in {@code written} can name a branch. */
  public record Named(@Nullable String name, TokenUsage spent) {}

  public Named name(String written) {
    Answer<String> summary = namer.nameBranch(written);
    String summarised = summary.facts().map(TaskName::summarised).orElse(null);
    return new Named(summarised != null ? summarised : TaskName.from(written), summary.usage());
  }

  /** Only now does the task exist, so only now can the call that named it be charged to it. */
  public void charge(String taskId, TokenUsage spent) {
    usage.chargeTask(taskId, spent);
  }
}
