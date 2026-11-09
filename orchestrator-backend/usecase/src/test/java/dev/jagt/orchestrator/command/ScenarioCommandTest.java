package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ScenarioSessions;
import dev.jagt.orchestrator.task.ProjectConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScenarioCommandTest {

  private final ScenarioSessions sessions = mock(ScenarioSessions.class);
  private final ConfigService config = mock(ConfigService.class);
  private final ScenarioCommand command = new ScenarioCommand(sessions, config);

  @Test
  void opensTheOnlyProjectsSessionWhenNoneIsNamed() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of("alpha", new ProjectConfig("/a", "origin/main", "dev", List.of()))));

    command.run("");

    verify(sessions).open("alpha", "");
  }

  @Test
  void opensTheProjectNamedWhereSeveralExist() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "alpha", new ProjectConfig("/a", "origin/main", "dev", List.of()),
                        "beta", new ProjectConfig("/b", "origin/main", "dev", List.of()))));

    command.run(" beta ");

    verify(sessions).open("beta", "");
  }

  @Test
  void refusesWhereSeveralProjectsExistAndNoneIsNamed() {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "alpha", new ProjectConfig("/a", "origin/main", "dev", List.of()),
                        "beta", new ProjectConfig("/b", "origin/main", "dev", List.of()))));

    assertThatThrownBy(() -> command.run("")).isInstanceOf(Refusal.class);
    verifyNoInteractions(sessions);
  }
}
