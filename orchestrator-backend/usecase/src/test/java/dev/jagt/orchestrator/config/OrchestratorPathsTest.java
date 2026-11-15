package dev.jagt.orchestrator.config;

import static dev.jagt.orchestrator.service.TestNewRepoBuilder.api;
import static dev.jagt.orchestrator.service.TestNewRepoBuilder.web;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrchestratorPathsTest {

  @Test
  void cutsTheDeployAndRevertWorktreesOfTaskUnderTheDataFolderPerProject(@TempDir Path data) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(data.toString()).withData(data.toString()));

    assertThat(paths.deployWorktree("web", "ABC-1"))
        .isEqualTo(data.resolve("worktrees/web/ABC-1-deploy"));
    assertThat(paths.revertWorktree("web", "ABC-1"))
        .isEqualTo(data.resolve("worktrees/web/ABC-1-revert"));
  }

  @Test
  void listsTheDeployWorktreeOnlyOfRepositoriesThatHaveDeployBranch(@TempDir Path data) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(data.toString()).withData(data.toString()));

    assertThat(
            paths.deployWorktrees(
                List.of(
                    api(data).build(),
                    web(data).withDeployBranch("").build(),
                    web(data).withProject("web2").build()),
                "ABC-1"))
        .containsExactly(
            data.resolve("worktrees/api/ABC-1-deploy"),
            data.resolve("worktrees/web2/ABC-1-deploy"));
  }

  @Test
  void takesTheFileNamedOnTheCommandLineOverTheOneItWouldHaveGoneLookingFor(@TempDir Path root) {
    Path named = root.resolve("elsewhere").resolve("jagt.yml");

    assertThat(
            OrchestratorPaths.configFileOutside(
                new String[] {"--orchestrator.config-file=" + named}))
        .isEqualTo(named);
  }

  @Test
  void answersInsideTheRootItWasGivenRatherThanWalkingUpForMarker(@TempDir Path root) {
    assertThat(OrchestratorPaths.configFileOutside(new String[] {"--orchestrator.root=" + root}))
        .isEqualTo(root.resolve("jagt.yml"));
  }

  @Test
  void keepsThePinnedFileWhenTheConfigItselfMovesTheRoot(@TempDir Path root) throws Exception {
    Path pinned = Files.writeString(root.resolve("jagt.yml"), "orchestrator: {}\n");
    OrchestratorProperties moved =
        OrchestratorProperties.defaults()
            .withRoot(root.resolve("somewhere-else").toString())
            .withConfigFile(pinned.toString());

    assertThat(new OrchestratorPaths(moved).configFile()).isEqualTo(pinned);
  }

  @Test
  void testJvmNeverResolvesTheHumansRoot() {
    Path root = new OrchestratorPaths(OrchestratorProperties.defaults()).root();

    assertThat(root)
        .isEqualTo(Path.of(System.getenv("ORCHESTRATOR_ROOT")).toAbsolutePath().normalize());
  }

  @Test
  void keepsScratchFoldersBesideTheStateFile(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("work/state.json").toString()));

    assertThat(paths.scratch()).isEqualTo(root.resolve("work/scratch"));
  }

  @Test
  void keepsStateAndWorktreesUnderTheDataFolderItWasGiven(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withData(root.resolve("vault").toString()));

    assertThat(paths.stateFile()).isEqualTo(root.resolve("vault/state.json"));
    assertThat(paths.masterToken()).isEqualTo(root.resolve("vault/master-token"));
    assertThat(paths.worktrees()).isEqualTo(root.resolve("vault/worktrees"));
  }

  @Test
  void keepsTheOldStatePlaceInTheRoot(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withData(root.resolve("vault").toString()));

    assertThat(paths.legacyStateFile()).isEqualTo(root.resolve("state.json"));
  }
}
