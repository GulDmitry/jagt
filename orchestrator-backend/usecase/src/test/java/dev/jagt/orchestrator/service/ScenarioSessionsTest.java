package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.RoleFiles;
import dev.jagt.orchestrator.config.RoleOptions;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.port.CheckerReader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScenarioSessionsTest {

  private final RoleSettings settings = mock(RoleSettings.class);
  private final RoleFiles brief = mock(RoleFiles.class);
  private final ScenarioWindows windows = mock(ScenarioWindows.class);
  private final ScenarioSessions sessions = new ScenarioSessions(settings, brief, windows);

  @Test
  void opensTheSessionInTheWorkspaceWithTheScenariosSectionAndWhatIsLoaded(@TempDir Path root) {
    when(settings.load())
        .thenReturn(RoleOptions.defaults().withEnabled(true).withWorkspace(root.toString()));
    when(brief.section("scenarios")).thenReturn("write scenarios");

    sessions.open("alpha", "Task ABC-42.");

    verify(windows)
        .open(
            "alpha",
            "",
            "write scenarios\n\nProject: alpha\nTask ABC-42.",
            new CheckerReader.Workspace(root, List.of()));
  }

  @Test
  void fallsBackToTheWholeBriefWhereItHasNoScenariosSection(@TempDir Path root) {
    when(settings.load())
        .thenReturn(RoleOptions.defaults().withEnabled(true).withWorkspace(root.toString()));
    when(brief.section("scenarios")).thenReturn("");
    when(brief.text()).thenReturn("the brief");

    sessions.open("alpha", "");

    verify(windows)
        .open("alpha", "", "the brief\n\nProject: alpha", new CheckerReader.Workspace(root, List.of()));
  }

  @Test
  void refusesWhereAcceptanceIsOff() {
    when(settings.load()).thenReturn(RoleOptions.defaults());

    assertThatThrownBy(() -> sessions.open("alpha", "")).isInstanceOf(Refusal.class);
    verifyNoInteractions(windows);
  }

  @Test
  void refusesWhereAcceptanceHoldsNoWorkspace() {
    when(settings.load()).thenReturn(RoleOptions.defaults().withEnabled(true));
    when(settings.file()).thenReturn("roles/acceptance/role.yml");

    assertThatThrownBy(() -> sessions.open("alpha", "")).isInstanceOf(Refusal.class);
    verifyNoInteractions(windows);
  }
}
