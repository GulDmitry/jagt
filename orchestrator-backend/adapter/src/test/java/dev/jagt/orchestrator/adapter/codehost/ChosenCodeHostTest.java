package dev.jagt.orchestrator.adapter.codehost;

import static dev.jagt.orchestrator.adapter.codehost.TestShopProjectBuilder.shopProject;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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

  private String firstWordOfTheCommandRun() {
    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(processes).run(any(), any(Duration.class), command.capture());
    return Path.of(command.getValue().getFirst()).getFileName().toString();
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "NONE",
      value = {"NONE, NONE, gh", "gitlab, NONE, glab", "gitlab, github, gh"})
  void theDriverIsChosenByTheProjectsKeyThenTheOrchestratorsThenGitHub(
      String orchestratorKey, String projectKey, String program) {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withCodeHost(orchestratorKey)
                .withProjects(Map.of("shop", shopProject().withKind(projectKey).build())));
    when(processes.run(any(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "[]", ""));

    pipelines.onCommit("shop", REMOTE, "0123abc");

    assertThat(firstWordOfTheCommandRun()).isEqualTo(program);
  }

  @Test
  void projectChoosingCommandReadsTheStateItsCommandPrints() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        shopProject()
                            .withKind("command")
                            .withPipelineCommand(List.of("/bin/ci-state", "{sha}"))
                            .build())));
    when(processes.run(any(), any(Duration.class), anyList())).thenReturn(
        new Processes.Result(0, "success", ""));

    Answer<CommitPipeline> answer = pipelines.onCommit("shop", REMOTE, "0123abc");

    assertThat(answer.facts()).contains(new CommitPipeline("success", ""));
  }

  @Test
  void projectChoosingNoneSettlesWithoutReadingAnything() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(Map.of("shop", shopProject().withKind("none").build())));

    Answer<CommitPipeline> answer = pipelines.onCommit("shop", REMOTE, "0123abc");

    assertThat(answer.facts().orElseThrow().hosted()).isFalse();
    verifyNoInteractions(processes);
  }

  @Test
  void kindNoDriverAnswersForIsUnreadNamingTheKey() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(Map.of("shop", shopProject().withKind("bitbucket").build())));

    Answer<CommitPipeline> answer = pipelines.onCommit("shop", REMOTE, "0123abc");

    assertThat(answer.failure()).startsWith("orchestrator.projects.shop.codeHost: 'bitbucket'");
  }

  @Test
  void settlesDeployWithNoPipelineForProjectWithNoRemoteAndNoCodeHostKey() {
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "shop",
                        new ProjectConfig(
                            new ProjectConfig.Repository("/repo/shop", List.of(), null, List.of()),
                            new ProjectConfig.Branches("main", "dev", "none"),
                            new ProjectConfig.CodeHost(
                                null, null, List.of(), List.of(), List.of(), List.of(), List.of()),
                            new ProjectConfig.Verify(List.of(), null)))));

    Answer<CommitPipeline> answer = pipelines.onCommit("shop", "", "0123abc");

    assertThat(answer.facts().orElseThrow().hosted()).isFalse();
  }

  @Test
  void noRequestIsClosedWhereTheRemoteIsLocalPath() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(Map.of("shop", shopProject().withKind("github").build())));

    Answer<String> answer =
        pipelines.closeRequest(
            "shop", "/srv/repos/web", "https://github.example.invalid/other-org/other-repo/pull/9");

    assertThat(answer.failure()).isEqualTo(LinkRefusal.HOSTLESS_REMOTE.said());
    verifyNoInteractions(processes);
  }

  @Test
  void noRequestIsClosedOnHostOtherThanTheRemotesWhateverItsPath() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(Map.of("shop", shopProject().withKind("github").build())));

    Answer<String> answer =
        pipelines.closeRequest("shop", REMOTE, "https://other.example.invalid/shop/web/pull/9");

    assertThat(answer.failure()).isEqualTo(LinkRefusal.OTHER_HOST.said());
    verifyNoInteractions(processes);
  }

  @Test
  void theNotesAreReadThroughTheDriverTheProjectChose() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(Map.of("shop", shopProject().withKind("none").build())));

    Answer<List<String>> answer =
        pipelines.unresolvedNotes(
            "shop", "https://git.example.invalid/shop/web/-/merge_requests/7");

    assertThat(answer.facts()).contains(List.of());
  }

  @Nested
  class ShopOnGitlab {

    @BeforeEach
    void chooseGitlab() {
      when(configService.load())
          .thenReturn(
              ConfigFile.defaults()
                  .withProjects(Map.of("shop", shopProject().withKind("gitlab").build())));
      when(processes.run(any(), any(Duration.class), anyList()))
          .thenReturn(new Processes.Result(0, "[]", ""));
    }

    @Test
    void branchIsReadThroughTheDriverTheKeyChooses() {
      Answer<CommitPipeline> answer = pipelines.onBranchSince("shop", REMOTE, "dev", "0123abc");

      assertThat(firstWordOfTheCommandRun()).isEqualTo("glab");
      assertThat(answer.facts()).contains(new CommitPipeline("none", ""));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://git.example.invalid/shop/web.git",
          "https://github.corp.invalid/shop/web.git"
        })
    void commitIsReadThroughGlabWhateverTheHostIsNamedWhereTheKeyChoosesIt(String remote) {
      pipelines.onCommit("shop", remote, "0123abc");

      assertThat(firstWordOfTheCommandRun()).isEqualTo("glab");
    }

    @Test
    void requestIsClosedThroughTheDriverTheKeyChooses() {
      when(processes.run(any(), any(Duration.class), anyList()))
          .thenReturn(new Processes.Result(0, "", "Closed"));

      Answer<String> answer =
          pipelines.closeRequest(
              "shop", REMOTE, "https://git.example.invalid/shop/web/-/merge_requests/7");

      assertThat(firstWordOfTheCommandRun()).isEqualTo("glab");
      assertThat(answer.facts()).isPresent();
    }
  }
}
