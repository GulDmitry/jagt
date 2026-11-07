package dev.jagt.orchestrator.adapter.codehost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.service.ReachLedger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CommandPipelineTest {

  private final Processes processes = mock(Processes.class);
  private final ReachLedger reach = new ReachLedger();
  private final CommandPipeline command = new CommandPipeline(processes, reach);
  private final HostSettings host =
      new HostSettings(
          "shop",
          "orchestrator.projects.shop.codeHost",
          Path.of("/repo/shop"),
          new dev.jagt.orchestrator.task.ProjectConfig.CodeHost(
              null,
              null,
              List.of("/bin/ci-state", "{sha}"),
              List.of("/bin/ci-rerun", "{pipeline}"),
              List.of(),
              List.of(),
              List.of()));

  @Test
  void theStatePrintedForTheCommitIsTheCommitsPipeline() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "running\n", ""));

    Answer<CommitPipeline> answer = command.onCommit(host, "git@anywhere:shop/web.git", "0123abc");

    ArgumentCaptor<List<String>> run = ArgumentCaptor.captor();
    verify(processes).run(any(Path.class), any(Duration.class), run.capture());
    assertThat(run.getValue()).containsExactly("/bin/ci-state", "0123abc");
    assertThat(answer.facts()).contains(new CommitPipeline("running", ""));
  }

  @Test
  void aBranchIsNotReadByACommandThatReadsOneCommit() {
    Answer<CommitPipeline> answer =
        command.onBranchSince(host, "git@anywhere:shop/web.git", "dev", "0123abc");

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure())
        .isEqualTo("orchestrator.projects.shop.pipelineCommand reads one commit, not a branch");
  }

  @Test
  void aWordOutsideTheFiveIsAnUnreadPipelineNamingTheKey() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "green", ""));

    Answer<CommitPipeline> answer = command.onCommit(host, "git@anywhere:shop/web.git", "0123abc");

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure())
        .startsWith("orchestrator.projects.shop.pipelineCommand printed 'green'");
  }

  @Test
  void aCommandExitingNonZeroSaysWhatItSaid() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(3, "", "token expired"));

    Answer<CommitPipeline> answer = command.onCommit(host, "git@anywhere:shop/web.git", "0123abc");

    assertThat(answer.failure())
        .isEqualTo("orchestrator.projects.shop.pipelineCommand exited 3: token expired");
  }

  @Test
  void aCommandExitingSeventySevenIsSignedOutAndTheProjectsLoginCommandIsTheFix() {
    HostSettings withLogin =
        new HostSettings(
            "shop",
            "orchestrator.codeHost",
            Path.of("/repo/shop"),
            new dev.jagt.orchestrator.task.ProjectConfig.CodeHost(
                null,
                null,
                List.of("/bin/ci-state", "{sha}"),
                List.of(),
                List.of("ci-login", "--web"),
                List.of(),
                List.of()));
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(77, "", ""));

    Answer<CommitPipeline> answer =
        command.onCommit(withLogin, "git@anywhere:shop/web.git", "0123abc");

    assertThat(answer.reach().orElseThrow().fix()).containsExactly("ci-login", "--web");
  }

  @Test
  void aCommandExitingSeventySevenIsHeldAsLostReachUntilAReadSucceeds() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(77, "", ""));

    command.onCommit(host, "git@anywhere:shop/web.git", "0123abc");

    assertThat(reach.standing()).extracting(ReachLost::driver).containsExactly("command");
  }

  @Test
  void aCommandNotInstalledIsLostReachWithNoCommandToFixIt() {
    HostSettings uninstalled =
        new HostSettings(
            "shop",
            "orchestrator.codeHost",
            Path.of("/repo/shop"),
            new dev.jagt.orchestrator.task.ProjectConfig.CodeHost(
                null,
                null,
                List.of("no-such-ci-program", "{sha}"),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenThrow(new IllegalStateException("Failed to start command"));

    Answer<CommitPipeline> answer =
        command.onCommit(uninstalled, "git@anywhere:shop/web.git", "0123abc");

    assertThat(answer.reach().orElseThrow().fix()).isEmpty();
  }

  @Test
  void theNextCommandThatPrintsAStateEndsTheLostReach() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(77, "", ""))
        .thenReturn(new Processes.Result(0, "success\n", ""));
    command.onCommit(host, "git@anywhere:shop/web.git", "0123abc");

    command.onCommit(host, "git@anywhere:shop/web.git", "0123abc");

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void aProjectWithoutAPipelineCommandIsUnreadWithoutRunningAnything() {
    HostSettings bare =
        new HostSettings(
            "shop",
            "orchestrator.codeHost",
            Path.of("/repo/shop"),
            new dev.jagt.orchestrator.task.ProjectConfig.CodeHost(
                null, null, List.of(), List.of(), List.of(), List.of(), List.of()));

    Answer<CommitPipeline> answer = command.onCommit(bare, "git@anywhere:shop/web.git", "0123abc");

    assertThat(answer.failure()).isEqualTo("orchestrator.projects.shop.pipelineCommand is not set");
    verifyNoInteractions(processes);
  }

  @Test
  void theRetryCommandGetsThePipelineAddress() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "ok", ""));

    Answer<String> answer = command.retryFailed(host, "https://ci.example.invalid/runs/9");

    ArgumentCaptor<List<String>> run = ArgumentCaptor.captor();
    verify(processes).run(any(Path.class), any(Duration.class), run.capture());
    assertThat(run.getValue())
        .containsExactly("/bin/ci-rerun", "https://ci.example.invalid/runs/9");
    assertThat(answer.facts()).isPresent();
  }

  @Test
  void withoutARetryCommandNothingIsReRun() {
    HostSettings noRetry =
        new HostSettings(
            "shop",
            "orchestrator.codeHost",
            Path.of("/repo/shop"),
            new dev.jagt.orchestrator.task.ProjectConfig.CodeHost(
                null,
                null,
                List.of("/bin/ci-state", "{sha}"),
                List.of(),
                List.of(),
                List.of(),
                List.of()));

    Answer<String> answer = command.retryFailed(noRetry, "https://ci.example.invalid/runs/9");

    assertThat(answer.failure()).isEqualTo("orchestrator.projects.shop.retryCommand is not set");
  }

  @Test
  void startupRefusesACommandWhoseProgramIsNowhereNamingTheKey() {
    HostSettings missing =
        new HostSettings(
            "shop",
            "orchestrator.codeHost",
            Path.of("/repo/shop"),
            new dev.jagt.orchestrator.task.ProjectConfig.CodeHost(
                null,
                null,
                List.of("no-such-ci-program", "{sha}"),
                List.of(),
                List.of(),
                List.of(),
                List.of()));

    assertThat(command.problems(missing))
        .extracting(Problem::key)
        .containsExactly("orchestrator.projects.shop.pipelineCommand");
  }

  @Test
  void startupRefusesACommandHostWithoutAPipelineCommand() {
    HostSettings bare =
        new HostSettings(
            "shop",
            "orchestrator.codeHost",
            Path.of("/repo/shop"),
            new dev.jagt.orchestrator.task.ProjectConfig.CodeHost(
                null, null, List.of(), List.of(), List.of(), List.of(), List.of()));

    assertThat(command.problems(bare))
        .extracting(Problem::key)
        .containsExactly("orchestrator.projects.shop.pipelineCommand");
  }

  @Test
  void theCloseCommandGetsTheRequestAddress() {
    HostSettings closing =
        new HostSettings(
            "shop",
            "orchestrator.codeHost",
            Path.of("/repo/shop"),
            new dev.jagt.orchestrator.task.ProjectConfig.CodeHost(
                null,
                null,
                List.of("/bin/ci-state", "{sha}"),
                List.of(),
                List.of(),
                List.of("/bin/ci-close", "{request}"),
                List.of()));
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "ok", ""));

    Answer<String> answer = command.closeRequest(closing, "https://ci.example.invalid/requests/9");

    ArgumentCaptor<List<String>> run = ArgumentCaptor.captor();
    verify(processes).run(any(Path.class), any(Duration.class), run.capture());
    assertThat(run.getValue())
        .containsExactly("/bin/ci-close", "https://ci.example.invalid/requests/9");
    assertThat(answer.facts()).isPresent();
  }

  @Test
  void withoutACloseCommandNoRequestIsClosed() {
    Answer<String> answer = command.closeRequest(host, "https://ci.example.invalid/requests/9");

    assertThat(answer.failure()).isEqualTo("orchestrator.projects.shop.closeCommand is not set");
    verifyNoInteractions(processes);
  }

  @Test
  void aCommandHostReadsNoNotesAndRunsNothing() {
    Answer<List<String>> answer =
        command.unresolvedNotes(host, "https://ci.example.invalid/requests/9");

    assertThat(answer.facts()).contains(List.of());
    verifyNoInteractions(processes);
  }

  @Test
  void theNotesCommandPrintsTheRequestsNoteIdsOnePerLine() {
    HostSettings noting =
        new HostSettings(
            "shop",
            "orchestrator.codeHost",
            Path.of("/repo/shop"),
            new dev.jagt.orchestrator.task.ProjectConfig.CodeHost(
                null,
                null,
                List.of("/bin/ci-state", "{sha}"),
                List.of(),
                List.of(),
                List.of(),
                List.of("/bin/ci-notes", "{request}")));
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "n-7\n\n n-9 \n", ""));

    Answer<List<String>> answer =
        command.unresolvedNotes(noting, "https://ci.example.invalid/requests/9");

    ArgumentCaptor<List<String>> run = ArgumentCaptor.captor();
    verify(processes).run(any(Path.class), any(Duration.class), run.capture());
    assertThat(run.getValue())
        .containsExactly("/bin/ci-notes", "https://ci.example.invalid/requests/9");
    assertThat(answer.facts()).contains(List.of("n-7", "n-9"));
  }

  @Test
  void aFailingNotesCommandLeavesTheNoteIdsUnknownNamingTheKey() {
    HostSettings noting =
        new HostSettings(
            "shop",
            "orchestrator.codeHost",
            Path.of("/repo/shop"),
            new dev.jagt.orchestrator.task.ProjectConfig.CodeHost(
                null,
                null,
                List.of("/bin/ci-state", "{sha}"),
                List.of(),
                List.of(),
                List.of(),
                List.of("/bin/ci-notes", "{request}")));
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(2, "", "rate limited"));

    Answer<List<String>> answer =
        command.unresolvedNotes(noting, "https://ci.example.invalid/requests/9");

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure())
        .isEqualTo("orchestrator.projects.shop.notesCommand exited 2: rate limited");
  }
}
