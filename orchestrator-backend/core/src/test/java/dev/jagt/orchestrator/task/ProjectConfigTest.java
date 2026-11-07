package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ProjectConfigTest {

  @Test
  void tellsARouterWhatTheRepositoryIsInTheWordsTheInstallWrote() {
    var project =
        new ProjectConfig(
            new ProjectConfig.Repository(
                "/repos/parser", List.of(), "the PDF parser behind the intake form", null),
            new ProjectConfig.Branches("origin/main", "dev", null),
            new ProjectConfig.CodeHost(null, null, null, null, null, null, null),
            new ProjectConfig.Verify(List.of(), null));

    assertThat(project.aboutOrPath()).isEqualTo("the PDF parser behind the intake form");
  }

  @Test
  void fallsBackToThePathWhereNobodyWroteOne() {
    var project = new ProjectConfig("/repos/parser", "origin/main", "dev", List.of());

    assertThat(project.aboutOrPath()).isEqualTo("/repos/parser");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "main        | refs/heads/main",
        "origin/main | refs/heads/main",
        "main        | origin/main",
        "main        | refs/remotes/origin/main",
        "main        | +refs/heads/main",
        "main        | ' main '",
        "refs/heads/main | main"
      })
  void deploysIntoTheBaseBranchWhenBothNameItInAnotherSpelling(String base, String deploy) {
    var project = new ProjectConfig("/repos/parser", base, deploy, List.of());

    assertThat(project.deploysIntoTheBaseBranch()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"main,dev", "main,''", "'',main", "origin/main,origin/dev"})
  void doesNotDeployIntoTheBaseBranchWhenTheDeployBranchIsAnotherOrBlank(
      String base, String deploy) {
    var project = new ProjectConfig("/repos/parser", base, deploy, List.of());

    assertThat(project.deploysIntoTheBaseBranch()).isFalse();
  }

  @Test
  void writesTheBaseBranchAsALocalName() {
    var project = new ProjectConfig("/repos/parser", "origin/main", "dev", List.of());

    assertThat(project.baseBranchName()).isEqualTo("main");
  }

  @Test
  void listsTheHumanPathsTheInstallWroteAndNoneWhereItWroteNone() {
    var declared =
        new ProjectConfig(
            new ProjectConfig.Repository(
                "/repos/parser", List.of(), null, List.of("db/migration/**")),
            new ProjectConfig.Branches("main", "dev", null),
            new ProjectConfig.CodeHost(null, null, null, null, null, null, null),
            new ProjectConfig.Verify(List.of(), null));
    var undeclared = new ProjectConfig("/repos/parser", "main", "dev", List.of());

    assertThat(declared.humanPathsOrNone()).containsExactly("db/migration/**");
    assertThat(undeclared.humanPathsOrNone()).isEmpty();
  }

  @Test
  void readsTheBaseBranchWithoutTheProjectsOwnRemotePrefix() {
    var project =
        new ProjectConfig(
            new ProjectConfig.Repository("/repos/parser", List.of(), null, null),
            new ProjectConfig.Branches("upstream/main", "dev", "upstream"),
            new ProjectConfig.CodeHost(null, null, null, null, null, null, null),
            new ProjectConfig.Verify(List.of(), null));

    assertThat(project.baseBranchName()).isEqualTo("main");
  }

  @Test
  void refusesToDeployIntoTheBaseBranchWhereTheRepositoryHasNoRemote() {
    var project =
        new ProjectConfig(
            new ProjectConfig.Repository("/repos/parser", List.of(), null, null),
            new ProjectConfig.Branches("main", "main", "none"),
            new ProjectConfig.CodeHost(null, null, null, null, null, null, null),
            new ProjectConfig.Verify(List.of(), null));

    assertThat(project.deploysIntoTheBaseBranch()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"0", "-3"})
  void limitsAVerifyCommandToTwentyMinutesWhereTheWrittenMinutesAreNotPositive(int written) {
    var project =
        new ProjectConfig(
            new ProjectConfig.Repository("/repos/parser", List.of(), null, null),
            new ProjectConfig.Branches("main", "dev", null),
            new ProjectConfig.CodeHost(null, null, null, null, null, null, null),
            new ProjectConfig.Verify(List.of(), written));

    assertThat(project.verifyTimeoutOrDefault()).isEqualTo(Duration.ofMinutes(20));
  }

  @Test
  void readsAndWritesTheFlatKeysOfAProjectEntry() {
    String written =
        """
        {"path":"/repos/parser","baseBranch":"upstream/main","deployBranch":"dev","labels":["pdf"],
         "verifyCommand":["make","check"],"about":"the parser","humanPaths":["db/**"],
         "codeHost":"gitlab","codeHostName":"main","pipelineCommand":["p"],"retryCommand":["r"],
         "loginCommand":["l"],"remote":"upstream","closeCommand":["c"],"notesCommand":["n"],"verifyMinutes":7}\
        """;
    JsonMapper mapper = JsonMapper.builder().build();

    ProjectConfig project = mapper.readValue(written, ProjectConfig.class);

    assertThat(project.path()).isEqualTo("/repos/parser");
    assertThat(project.verifyMinutes()).isEqualTo(7);
    assertThat(mapper.<JsonNode>valueToTree(project)).isEqualTo(mapper.readTree(written));
  }
}
