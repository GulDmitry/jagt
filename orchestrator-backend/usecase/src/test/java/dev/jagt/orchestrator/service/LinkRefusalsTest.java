package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestReportedBuilder.reported;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkRefusalsTest {

  @TempDir Path root;

  private StateService state;

  @BeforeEach
  void startsOneRunningTaskWithItsRemote() {
    state = stateService().withRoot(root).build();
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a42")
            .repos(List.of(new TaskRepo("proj", "/wt", "git@host:team/proj.git", null)))
            .build());
  }

  @Test
  void showsOneLineOnTheBoardsHeadForRefusedLink() {
    ReachLedger reach = new ReachLedger();

    new LinkRefusals(reach, mock(SessionLines.class), state)
        .onBoard(
            reported().withLink("https://host/other/proj/-/merge_requests/9").build(), "ABC-42");

    assertThat(reach.standing())
        .extracting(ReachLost::said)
        .containsExactly("a42 proj: request is not under team/proj");
  }

  @Test
  void nudgesTheSessionOnceNamingTheExpectedRemote() {
    SessionLines sessions = mock(SessionLines.class);

    new LinkRefusals(new ReachLedger(), sessions, state)
        .told(reported().withLink("https://host/other/proj/-/merge_requests/9").build(), "ABC-42");

    verify(sessions).say(eq("ABC-42"), contains("team/proj"));
  }

  @Test
  void nudgesNoSessionForLinkUnderTheRemote() {
    SessionLines sessions = mock(SessionLines.class);

    new LinkRefusals(new ReachLedger(), sessions, state)
        .told(reported().withLink("https://host/team/proj/-/merge_requests/9").build(), "ABC-42");

    verifyNoInteractions(sessions);
  }

  @Test
  void endsTheLineOnTheRepositorysNextGoodLink() {
    ReachLedger reach = new ReachLedger();
    new LinkRefusals(reach, mock(SessionLines.class), state)
        .onBoard(
            reported().withLink("https://host/other/proj/-/merge_requests/9").build(), "ABC-42");

    state.updateTask(
        "ABC-42", task -> task.withMrUrl("proj", "https://host/team/proj/-/merge_requests/9", 1L));

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void endsTheLineWhenTheTaskCloses() {
    ReachLedger reach = new ReachLedger();
    new LinkRefusals(reach, mock(SessionLines.class), state)
        .onBoard(
            reported().withLink("https://host/other/proj/-/merge_requests/9").build(), "ABC-42");

    state.removeTask("ABC-42");

    assertThat(reach.standing()).isEmpty();
  }
}
