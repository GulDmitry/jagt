package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.task.AssistantCallKind;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import lombok.With;

@With
public record TestOpenCodeReadBuilder(
    Path cwd, String schema, List<String> shell, List<String> refuseRead, boolean git) {

  public static TestOpenCodeReadBuilder openCodeRead() {
    return new TestOpenCodeReadBuilder(
        Path.of("/wt"), "{}", List.of("git log*"), List.of(), true);
  }

  public OpenCodeRead build() {
    return new OpenCodeRead(
        cwd,
        "count the commits",
        schema,
        "qwen-4b",
        OpenCodeScope.of(shell, List.of(), refuseRead, false, git, List.of(cwd), List.of()),
        AssistantCallKind.ACCEPTANCE_CHECK,
        Duration.ofMinutes(1));
  }
}
