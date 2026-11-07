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
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.task.ProjectConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChosenCodeHostTest {

  private static final String REMOTE = "https://git.example.invalid/shop/web.git";

  private final ConfigService configService = mock(ConfigService.class);
  private final Processes processes = mock(Processes.class);
  private final ReachLedger reach = new ReachLedger();
  private final ChosenCodeHost pipelines =
      new ChosenCodeHost(
          configService,
          List.of(
              new GitHubCli(processes, reach),
              new GitLabCli(processes, reach),
              new CommandPipeline(processes, reach),
              new NoCodeHost()));

  private static ProjectConfig project(String codeHost, List<String> pipelineCommand) {
    return new ProjectConfig(
        new ProjectConfig.Repository("/repo/shop", List.of(), null, null),
        new ProjectConfig.Branches("origin/main", "dev", null),
        new ProjectConfig.CodeHost(codeHost, null, pipelineCommand, null, null, null, null),
        new ProjectConfig.Verify(List.of(), null));
  }

  private String firstWordOfTheCommandRun() {
    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(any(), any(Duration.class), command.capture());
    return Path.of(command.getValue().getFirst()).getFileName().toString();
  }

  @Test
  void theCodeHostIsGitHubWhereNoKeyNamesOne() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withProjects(Map.of("shop", project(null, null))));
    when(processes.run(any(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    pipelines.onCommit("shop", REMOTE, "0123abc");

    assertThat(firstWordOfTheCommandRun()).isEqualTo("gh");
  }

  @Test
  void aBranchIsReadThroughTheDriverTheKeyChooses() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withProjects(Map.of("shop", project("gitlab", null))));
    when(processes.run(any(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    Answer<CommitPipeline> answer = pipelines.onBranchSince("shop", REMOTE, "dev", "0123abc");

    assertThat(firstWordOfTheCommandRun()).isEqualTo("glab");
    assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
  }

  @Test
  void aHostWithoutGitlabInItsNameIsReadThroughGlabWhereTheKeyChoosesIt() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withProjects(Map.of("shop", project("gitlab", null))));
    when(processes.run(any(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    pipelines.onCommit("shop", REMOTE, "0123abc");

    assertThat(firstWordOfTheCommandRun()).isEqualTo("glab");
  }

  @Test
  void aHostNamedAfterGithubIsReadThroughGlabWhereTheKeyChoosesIt() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withProjects(Map.of("shop", project("gitlab", null))));
    when(processes.run(any(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    pipelines.onCommit("shop", "https://github.corp.invalid/shop/web.git", "0123abc");

    assertThat(firstWordOfTheCommandRun()).isEqualTo("glab");
  }

  @Test
  void theOrchestratorsKeyAppliesToAProjectThatNamesNone() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withCodeHost("gitlab")
                .withProjects(Map.of("shop", project(null, null))));
    when(processes.run(any(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    pipelines.onCommit("shop", REMOTE, "0123abc");

    assertThat(firstWordOfTheCommandRun()).isEqualTo("glab");
  }

  @Test
  void theProjectsKeyWinsOverTheOrchestrators() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withCodeHost("gitlab")
                .withProjects(Map.of("shop", project("github", null))));
    when(processes.run(any(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    pipelines.onCommit("shop", REMOTE, "0123abc");

    assertThat(firstWordOfTheCommandRun()).isEqualTo("gh");
  }

  @Test
  void aProjectChoosingCommandReadsTheStateItsCommandPrints() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of("shop", project("command", List.of("/bin/ci-state", "{sha}")))));
    when(processes.run(any(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "success", ""));

    Answer<CommitPipeline> answer = pipelines.onCommit("shop", REMOTE, "0123abc");

    assertThat(answer.facts()).contains(new CommitPipeline("success", ""));
  }

  @Test
  void aProjectChoosingNoneSettlesWithoutReadingAnything() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withProjects(Map.of("shop", project("none", null))));

    Answer<CommitPipeline> answer = pipelines.onCommit("shop", REMOTE, "0123abc");

    assertThat(answer.facts().orElseThrow().hosted()).isFalse();
    verifyNoInteractions(processes);
  }

  @Test
  void aKindNoDriverAnswersForIsUnreadNamingTheKey() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withProjects(Map.of("shop", project("bitbucket", null))));

    Answer<CommitPipeline> answer = pipelines.onCommit("shop", REMOTE, "0123abc");

    assertThat(answer.failure()).startsWith("orchestrator.projects.shop.codeHost: 'bitbucket'");
  }

  @Test
  void settlesADeployWithNoPipelineForAProjectWithNoRemoteAndNoCodeHostKey() {
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        new ProjectConfig(
                            new ProjectConfig.Repository("/repo/shop", List.of(), null, null),
                            new ProjectConfig.Branches("main", "dev", "none"),
                            new ProjectConfig.CodeHost(null, null, null, null, null, null, null),
                            new ProjectConfig.Verify(List.of(), null)))));

    Answer<CommitPipeline> answer = pipelines.onCommit("shop", "", "0123abc");

    assertThat(answer.facts().orElseThrow().hosted()).isFalse();
  }

  @Test
  void aRequestIsClosedThroughTheDriverTheKeyChooses() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withProjects(Map.of("shop", project("gitlab", null))));
    when(processes.run(any(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", "Closed"));

    Answer<String> answer =
        pipelines.closeRequest(
            "shop", REMOTE, "https://git.example.invalid/shop/web/-/merge_requests/7");

    assertThat(firstWordOfTheCommandRun()).isEqualTo("glab");
    assertThat(answer.facts()).isPresent();
  }

  @Test
  void noRequestIsClosedWhereTheRemoteIsALocalPath() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withProjects(Map.of("shop", project("github", null))));

    Answer<String> answer =
        pipelines.closeRequest(
            "shop", "/srv/repos/web", "https://github.example.invalid/other-org/other-repo/pull/9");

    assertThat(answer.failure()).isEqualTo(LinkRefusal.HOSTLESS_REMOTE.said());
    verifyNoInteractions(processes);
  }

  @Test
  void noRequestIsClosedOnAHostOtherThanTheRemotesWhateverItsPath() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withProjects(Map.of("shop", project("github", null))));

    Answer<String> answer =
        pipelines.closeRequest("shop", REMOTE, "https://other.example.invalid/shop/web/pull/9");

    assertThat(answer.failure()).isEqualTo(LinkRefusal.OTHER_HOST.said());
    verifyNoInteractions(processes);
  }

  @Test
  void theNotesAreReadThroughTheDriverTheProjectChose() {
    when(configService.load())
        .thenReturn(ConfigFile.defaults().withProjects(Map.of("shop", project("none", null))));

    Answer<List<String>> answer =
        pipelines.unresolvedNotes(
            "shop", "https://git.example.invalid/shop/web/-/merge_requests/7");

    assertThat(answer.facts()).contains(List.of());
  }
}
