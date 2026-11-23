package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.nio.file.Path;
import java.util.List;
import lombok.With;

@With
public record TestHostSettingsBuilder(
    String key,
    Path repository,
    List<String> pipelineCommand,
    List<String> retryCommand,
    List<String> loginCommand,
    List<String> closeCommand,
    List<String> notesCommand,
    ProjectConfig.Review review) {

  public static TestHostSettingsBuilder hostSettings() {
    return new TestHostSettingsBuilder(
        "orchestrator.codeHost",
        Path.of("/repo/shop"),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        ProjectConfig.Review.NONE);
  }

  public HostSettings build() {
    return new HostSettings(
        "shop",
        key,
        repository,
        new ProjectConfig.CodeHost(
            null,
            null,
            pipelineCommand,
            retryCommand,
            loginCommand,
            closeCommand,
            notesCommand,
            review));
  }
}
