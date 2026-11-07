package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.protocol.Reported;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class LinkRefusalsTest {

  private static Reported ship(String url) {
    return new Reported(
        TaskStatus.CI_POLLING, AgentReport.PLAIN, "shipped", url, Map.of(), false, null, Map.of());
  }

  private static StateService state(Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a42")
            .repos(List.of(new TaskRepo("proj", "/wt", "git@host:team/proj.git", null)))
            .build());
    return state;
  }

  @Test
  void showsOneLineOnTheBoardsHeadForARefusedLink(@TempDir Path root) {
    ReachLedger reach = new ReachLedger();

    new LinkRefusals(reach, mock(SessionLines.class), state(root))
        .onBoard(ship("https://host/other/proj/-/merge_requests/9"), "ABC-42");

    assertThat(reach.standing())
        .extracting(ReachLost::said)
        .containsExactly("a42 proj: request is not under team/proj");
  }

  @Test
  void nudgesTheSessionOnceNamingTheExpectedRemote(@TempDir Path root) {
    SessionLines sessions = mock(SessionLines.class);

    new LinkRefusals(new ReachLedger(), sessions, state(root))
        .told(ship("https://host/other/proj/-/merge_requests/9"), "ABC-42");

    verify(sessions).say(eq("ABC-42"), contains("team/proj"));
  }

  @Test
  void nudgesNoSessionForALinkUnderTheRemote(@TempDir Path root) {
    SessionLines sessions = mock(SessionLines.class);

    new LinkRefusals(new ReachLedger(), sessions, state(root))
        .told(ship("https://host/team/proj/-/merge_requests/9"), "ABC-42");

    verifyNoInteractions(sessions);
  }

  @Test
  void endsTheLineOnTheRepositorysNextGoodLink(@TempDir Path root) {
    ReachLedger reach = new ReachLedger();
    StateService state = state(root);
    new LinkRefusals(reach, mock(SessionLines.class), state)
        .onBoard(ship("https://host/other/proj/-/merge_requests/9"), "ABC-42");

    state.updateTask(
        "ABC-42", task -> task.withMrUrl("proj", "https://host/team/proj/-/merge_requests/9", 1L));

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void endsTheLineWhenTheTaskCloses(@TempDir Path root) {
    ReachLedger reach = new ReachLedger();
    StateService state = state(root);
    new LinkRefusals(reach, mock(SessionLines.class), state)
        .onBoard(ship("https://host/other/proj/-/merge_requests/9"), "ABC-42");

    state.removeTask("ABC-42");

    assertThat(reach.standing()).isEmpty();
  }
}
