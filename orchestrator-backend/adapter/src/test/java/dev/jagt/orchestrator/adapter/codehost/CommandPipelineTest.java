package dev.jagt.orchestrator.adapter.codehost;

import static dev.jagt.orchestrator.adapter.codehost.TestHostSettingsBuilder.hostSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.CommitPipeline;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.reach.ReachLedger;
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
      hostSettings()
          .withKey("orchestrator.projects.shop.codeHost")
          .withPipelineCommand(List.of("/bin/ci-state", "{sha}"))
          .withRetryCommand(List.of("/bin/ci-rerun", "{pipeline}"))
          .build();

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
  void branchIsNotReadByCommandThatReadsOneCommit() {
    Answer<CommitPipeline> answer =
        command.onBranchSince(host, "git@anywhere:shop/web.git", "dev", "0123abc");

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure())
        .isEqualTo("orchestrator.projects.shop.pipelineCommand reads one commit, not a branch");
  }

  @Test
  void wordOutsideTheFiveIsAnUnreadPipelineNamingTheKey() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "green", ""));

    Answer<CommitPipeline> answer = command.onCommit(host, "git@anywhere:shop/web.git", "0123abc");

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure())
        .startsWith("orchestrator.projects.shop.pipelineCommand printed 'green'");
  }

  @Test
  void commandExitingNonZeroSaysWhatItSaid() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(3, "", "token expired"));

    Answer<CommitPipeline> answer = command.onCommit(host, "git@anywhere:shop/web.git", "0123abc");

    assertThat(answer.failure())
        .isEqualTo("orchestrator.projects.shop.pipelineCommand exited 3: token expired");
  }

  @Test
  void commandExitingSeventySevenIsSignedOutAndTheProjectsLoginCommandIsTheFix() {
    HostSettings withLogin =
        hostSettings()
            .withKey("orchestrator.codeHost")
            .withPipelineCommand(List.of("/bin/ci-state", "{sha}"))
            .withLoginCommand(List.of("ci-login", "--web"))
            .build();
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(77, "", ""));

    Answer<CommitPipeline> answer =
        command.onCommit(withLogin, "git@anywhere:shop/web.git", "0123abc");

    assertThat(answer.reach().orElseThrow().fix()).containsExactly("ci-login", "--web");
  }

  @Test
  void commandExitingSeventySevenIsHeldAsLostReachUntilReadSucceeds() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(77, "", ""));

    command.onCommit(host, "git@anywhere:shop/web.git", "0123abc");

    assertThat(reach.standing()).extracting(ReachLost::driver).containsExactly("command");
  }

  @Test
  void commandNotInstalledIsLostReachWithNoCommandToFixIt() {
    HostSettings uninstalled =
        hostSettings()
            .withKey("orchestrator.codeHost")
            .withPipelineCommand(List.of("no-such-ci-program", "{sha}"))
            .build();
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenThrow(new IllegalStateException("Failed to start command"));

    Answer<CommitPipeline> answer =
        command.onCommit(uninstalled, "git@anywhere:shop/web.git", "0123abc");

    assertThat(answer.reach().orElseThrow().fix()).isEmpty();
  }

  @Test
  void theNextCommandThatPrintsStateEndsTheLostReach() {
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(77, "", ""))
        .thenReturn(new Processes.Result(0, "success\n", ""));
    command.onCommit(host, "git@anywhere:shop/web.git", "0123abc");

    command.onCommit(host, "git@anywhere:shop/web.git", "0123abc");

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void projectWithoutPipelineCommandIsUnreadWithoutRunningAnything() {
    HostSettings bare = hostSettings().withKey("orchestrator.codeHost").build();

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
  void withoutRetryCommandNothingIsReRun() {
    HostSettings noRetry =
        hostSettings()
            .withKey("orchestrator.codeHost")
            .withPipelineCommand(List.of("/bin/ci-state", "{sha}"))
            .build();

    Answer<String> answer = command.retryFailed(noRetry, "https://ci.example.invalid/runs/9");

    assertThat(answer.failure()).isEqualTo("orchestrator.projects.shop.retryCommand is not set");
  }

  @Test
  void startupRefusesCommandWhoseProgramIsNowhereNamingTheKey() {
    HostSettings missing =
        hostSettings()
            .withKey("orchestrator.codeHost")
            .withPipelineCommand(List.of("no-such-ci-program", "{sha}"))
            .build();

    assertThat(command.problems(missing))
        .extracting(Problem::key)
        .containsExactly("orchestrator.projects.shop.pipelineCommand");
  }

  @Test
  void startupRefusesCommandHostWithoutPipelineCommand() {
    HostSettings bare = hostSettings().withKey("orchestrator.codeHost").build();

    assertThat(command.problems(bare))
        .extracting(Problem::key)
        .containsExactly("orchestrator.projects.shop.pipelineCommand");
  }

  @Test
  void theCloseCommandGetsTheRequestAddress() {
    HostSettings closing =
        hostSettings()
            .withKey("orchestrator.codeHost")
            .withPipelineCommand(List.of("/bin/ci-state", "{sha}"))
            .withCloseCommand(List.of("/bin/ci-close", "{request}"))
            .build();
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
  void withoutCloseCommandNoRequestIsClosed() {
    Answer<String> answer = command.closeRequest(host, "https://ci.example.invalid/requests/9");

    assertThat(answer.failure()).isEqualTo("orchestrator.projects.shop.closeCommand is not set");
    verifyNoInteractions(processes);
  }

  @Test
  void commandHostReadsNoNotesAndRunsNothing() {
    Answer<List<String>> answer =
        command.unresolvedNotes(host, "https://ci.example.invalid/requests/9");

    assertThat(answer.facts()).contains(List.of());
    verifyNoInteractions(processes);
  }

  @Test
  void theNotesCommandPrintsTheRequestsNoteIdsOnePerLine() {
    HostSettings noting =
        hostSettings()
            .withPipelineCommand(List.of("/bin/ci-state", "{sha}"))
            .withNotesCommand(List.of("/bin/ci-notes", "{request}"))
            .build();
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
  void failingNotesCommandLeavesTheNoteIdsUnknownNamingTheKey() {
    HostSettings noting =
        hostSettings()
            .withPipelineCommand(List.of("/bin/ci-state", "{sha}"))
            .withNotesCommand(List.of("/bin/ci-notes", "{request}"))
            .build();
    when(processes.run(any(Path.class), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(2, "", "rate limited"));

    Answer<List<String>> answer =
        command.unresolvedNotes(noting, "https://ci.example.invalid/requests/9");

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure())
        .isEqualTo("orchestrator.projects.shop.notesCommand exited 2: rate limited");
  }
}
