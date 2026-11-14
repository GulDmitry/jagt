package dev.jagt.orchestrator.board;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskState.TaskStateBuilder;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Path;
import java.util.List;
import lombok.With;

@With
public record TestAlphaAndBetaTaskBuilder(Path root, long lastActiveTimestamp) {

  public static TestAlphaAndBetaTaskBuilder alphaAndBetaTask(Path root) {
    return new TestAlphaAndBetaTaskBuilder(root, 0L);
  }

  public TaskStateBuilder build() {
    return TaskState.builder(
            List.of(
                TaskRepo.of("alpha", root.resolve("ABC-1-alpha").toString())
                    .withMrUrl("https://host.example/alpha/mr/7"),
                TaskRepo.of("beta", root.resolve("ABC-1-beta").toString())
                    .withMrUrl("https://host.example/beta/mr/7")),
            TaskStatus.CI_POLLING)
        .alias("a1")
        .lastActiveTimestamp(lastActiveTimestamp);
  }
}
