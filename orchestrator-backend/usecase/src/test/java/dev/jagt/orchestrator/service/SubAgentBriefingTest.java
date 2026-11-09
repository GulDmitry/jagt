package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubAgentBriefingTest {

  private final TicketScenarios scenarios = mock(TicketScenarios.class, call -> "");

  @Test
  void pointsTheAgentAtTheRequestTheTaskWasOpenedWithSinceLaterInstructionsReplaceTheContext(
      @TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing)
        .contains(
            "`task_request.md`, if present: the request the task opened with, never overwritten.");
  }

  @Test
  void namesLogicHardToFollowFromItsCodeAsWorthAPictureWhenVisualizeIsOn(@TempDir Path root)
      throws Exception {
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                new ConfigService.ConfigFile.VisualizeConfig(true),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing)
        .contains(
            "logic hard to follow from its code, such as rules that combine or rows and columns"
                + " that shift each other: draw a worked example, its input and its outcome");
  }

  @Test
  void grantsTheRebaseOntoTheMovedTargetWithALeaseAndStillForbidsForceAndMerge(@TempDir Path root)
      throws Exception {
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing)
        .contains(
            "No `git merge` or `cherry-pick`",
            "`git rebase` this branch onto its request's target",
            "then `push --force-with-lease`",
            "(`--force`, `--amend`,");
  }

  @Test
  void addsNothingForAContextSectionTheBriefLeavesEmpty(@TempDir Path root) throws Exception {
    Path blank = Files.createDirectories(root.resolve("blank"));
    Path absent = Files.createDirectories(root.resolve("absent"));
    Files.createDirectories(blank.resolve("roles/session"));
    Files.writeString(blank.resolve("roles/session/brief.md"), "## context\n");
    Files.createDirectories(absent.resolve("roles/session"));
    Files.writeString(absent.resolve("roles/session/brief.md"), "## other\nText.\n");
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String withBlank =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(blank.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());
    String withNone =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(absent.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(withBlank).isEqualTo(withNone);
  }

  @Test
  void namesTheDeployBranchAsJagtsAloneSoTheAgentNeverMergesIntoIt(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing).contains("The deploy branch `dev` is jagt's `deploy` alone.");
  }

  @Test
  void listsTheDeployWorktreeAConflictIsHandedOverInAsTheSessionsOwn(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing)
        .contains(
            "- Also yours while jagt hands you a deploy conflict there: /repo/ABC-42-deploy.");
  }

  @Test
  void namesTheDeployBranchOfEveryRepositoryOfTheTask(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo web =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);
    NewRepo api =
        new NewRepo(
            "api",
            new ProjectConfig("/repo/api", "origin/main", "stage", List.of()),
            Path.of("/repo/api"),
            Path.of("/w/ABC-42-api"),
            Path.of("/repo/api/.git"),
            "origin/main",
            "git@host:g/api.git",
            false);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                web,
                List.of(web, api),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing)
        .contains("The deploy branches `dev` (web), `stage` (api) are jagt's `deploy` alone.");
  }

  @Test
  void namesTheBaseBranchOfEveryRepositoryOfTheTaskReadOnly(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo web =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);
    NewRepo api =
        new NewRepo(
            "api",
            new ProjectConfig("/repo/api", "origin/develop", "stage", List.of()),
            Path.of("/repo/api"),
            Path.of("/w/ABC-42-api"),
            Path.of("/repo/api/.git"),
            "origin/develop",
            "git@host:g/api.git",
            false);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                web,
                List.of(web, api),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing)
        .contains("The base branches `origin/main` (web), `origin/develop` (api) are read-only.");
  }

  @Test
  void saysNothingOfADeployBranchWhenTheTaskHasNone(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", null, List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing).doesNotContain("deploy branch");
  }

  @Test
  void asksForAPlanFirstWhenAutoplanIsOnNamingTheFileCount(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults().withEnabled(true).withFiles(12),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing)
        .contains("plan first if the task changes more than 12 files")
        .contains("report IN_PROGRESS with outcome `no_plan`");
  }

  @Test
  void tellsTheSessionToPlanFirstWhenTheTaskWasOpenedInPlanMode(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").mode("plan").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing).contains("PLAN FIRST: the human asked for a plan.");
  }

  @Test
  void saysNothingOfPlanningFirstWhenTheTaskWasOpenedInAutoMode(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").mode("auto").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing).doesNotContain("PLAN FIRST");
  }

  @Test
  void saysNothingOfAutoplanWhenItIsLeftAtItsDefault(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing).doesNotContain("AUTOPLAN");
  }

  @Test
  void namesTheOtherWorktreesOfATaskAndNotTheOneTheAgentRunsIn(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo web =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);
    NewRepo api =
        new NewRepo(
            "api",
            new ProjectConfig("/repo/api", "origin/develop", "stage", List.of()),
            Path.of("/repo/api"),
            Path.of("/w/ABC-42-api"),
            Path.of("/repo/api/.git"),
            "origin/develop",
            "git@host:g/api.git",
            false);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                web,
                List.of(web, api),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing)
        .contains("- This task ALSO works in these worktrees")
        .contains("  - api: /w/ABC-42-api (cut from origin/develop)")
        .doesNotContain("  - web:");
  }

  @Test
  void saysATaskWorksInOneRepositoryOnlyWhereItHasNoOther(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo web =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                web,
                List.of(web),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing)
        .contains("- This task works in this repository only.")
        .doesNotContain("ALSO works");
  }

  @Test
  void asksForTaskVisualMdWhenVisualizeIsOn(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults().withEnabled(true),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing)
        .contains(
            "write `task_visual.md` in the worktree as part of the plan when you plan first, else"
                + " before your first edit")
        .contains("A two-line bug fix is not.");
  }

  @Test
  void tablesAndCallsBetweenServersAreAlwaysWorthAPictureWhenVisualizeIsOn(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults().withEnabled(true),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing)
        .contains(
            "A new or changed table, or a call between servers, services or a database, always is,")
        .contains(
            "an ER diagram draws every reference between the tables, a sequence diagram every"
                + " call");
  }

  @Test
  void warnsThatASemicolonEndsAMermaidStatementWhenVisualizeIsOn(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults().withEnabled(true),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing).contains("no `;` in a Mermaid label, where it ends the statement");
  }

  @Test
  void saysNothingOfAPictureWhenVisualizeIsLeftAtItsDefault(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing).doesNotContain("VISUALIZE").doesNotContain("task_visual.md");
  }

  @Test
  void asksForFactsAndAControlBeforeACauseWhenDiagnoseIsOn(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults().withEnabled(true));

    assertThat(briefing)
        .contains("DIAGNOSE: a bug is diagnosed from facts before code.")
        .contains("A repro counts only when it shows the report's signal.");
  }

  @Test
  void saysNothingOfDiagnosingWhenDiagnoseIsLeftAtItsDefault(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing).doesNotContain("DIAGNOSE");
  }

  @Test
  void keepsTheBranchRuleWhereTheHumansCopyDropsIt(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(
        root.resolve("roles/session/brief.md"), "## context\n\n<rules>\n1. Work.\n</rules>\n");
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing).contains("1. Work.", "<hard_safety>", "push only to `ABC-42`");
  }

  @Test
  void asksForTheJudgementOfEverySwitchThatIsOnInTheFirstReportAndThePlanHandBack(
      @TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                new ConfigService.ConfigFile.VisualizeConfig(true),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing)
        .contains(
            "first status report and the one handing back a plan carry `switches`: for each of"
                + " visualize");
  }

  @Test
  void tellsATaskWithNoRepositoryToNameEveryProjectItChangesInItsPlan(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))), scenarios)
            .ofScratch(
                NewTask.unplacedBuilder("ABC-42").mode("plan").build(),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing)
        .contains("## Repositories", "- [changed] <key>", "registered in", "No git of any kind")
        .contains("PLAN FIRST", "ASKING IS STOPPING", "no repository");
  }

  @Test
  void endsTheBriefingWithTheScenariosLineWhenThereIsOne(@TempDir Path root) throws Exception {
    NewRepo repo =
        new NewRepo(
            "web",
            new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
            Path.of("/repo/web"),
            Path.of("/w/ABC-42"),
            Path.of("/repo/web/.git"),
            "origin/main",
            "git@host:g/web.git",
            true);
    TicketScenarios found = mock(TicketScenarios.class);
    when(found.briefingLine("ABC-42")).thenReturn("\n\n<scenarios>rows</scenarios>");

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))),
                found)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                repo,
                List.of(repo),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing).endsWith("<scenarios>rows</scenarios>");
  }

  @Test
  void endsTheScratchBriefingWithTheScenariosLineWhenThereIsOne(@TempDir Path root)
      throws Exception {
    TicketScenarios found = mock(TicketScenarios.class);
    when(found.briefingLine("ABC-42")).thenReturn("\n\n<scenarios>rows</scenarios>");

    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))),
                found)
            .ofScratch(
                NewTask.builder("ABC-42", "web").build(),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(briefing).endsWith("<scenarios>rows</scenarios>");
  }
}
