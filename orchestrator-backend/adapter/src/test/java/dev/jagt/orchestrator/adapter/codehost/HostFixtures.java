package dev.jagt.orchestrator.adapter.codehost;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.task.ProjectConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.mockito.ArgumentCaptor;

final class HostFixtures {

  private HostFixtures() {}

  static ProjectConfig project(@Nullable String codeHost, List<String> pipelineCommand) {
    return new ProjectConfig(
        new ProjectConfig.Repository("/repo/shop", List.of(), null, List.of()),
        new ProjectConfig.Branches("origin/main", "dev", null),
        new ProjectConfig.CodeHost(
            codeHost, null, pipelineCommand, List.of(), List.of(), List.of(), List.of()),
        new ProjectConfig.Verify(List.of(), null));
  }

  static ConfigFile shopOn(@Nullable String codeHost) {
    return ConfigFile.defaults().withProjects(Map.of("shop", project(codeHost, List.of())));
  }

  static void serving(ConfigService configService, ConfigFile config) {
    when(configService.load()).thenReturn(config);
  }

  static void answerEveryRun(Processes processes, Processes.Result result) {
    when(processes.run(any(), any(Duration.class), anyList())).thenReturn(result);
  }

  static HostSettings settings(
      String key,
      List<String> pipeline,
      List<String> rerun,
      List<String> login,
      List<String> second,
      List<String> notes) {
    return new HostSettings(
        "shop",
        key,
        Path.of("/repo/shop"),
        new ProjectConfig.CodeHost(null, null, pipeline, rerun, login, second, notes));
  }

  static HostSettings notingHost() {
    return settings(
        "orchestrator.codeHost",
        List.of("/bin/ci-state", "{sha}"),
        List.of(),
        List.of(),
        List.of(),
        List.of("/bin/ci-notes", "{request}"));
  }

  static HostSettings bareRepoHost() {
    return new HostSettings(
        "shop",
        "orchestrator.codeHost",
        Path.of("/repo"),
        new ProjectConfig.CodeHost(
            null, null, List.of(), List.of(), List.of(), List.of(), List.of()));
  }

  static List<List<String>> twoCommandsOf(Processes processes) {
    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes, times(2)).run(isNull(), any(Duration.class), command.capture());
    return command.getAllValues();
  }
}
