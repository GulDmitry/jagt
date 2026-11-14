package dev.jagt.orchestrator.service.worktree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.scenario.TicketScenarios;
import dev.jagt.orchestrator.task.launch.NewTask;
import dev.jagt.orchestrator.task.repo.NewRepo;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubAgentBriefingTest {

  private final TicketScenarios scenarios = mock(TicketScenarios.class, call -> "");
  private final NewRepo web =
      new NewRepo(
          "web",
          new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
          Path.of("/repo/web"),
          Path.of("/w/ABC-42"),
          Path.of("/repo/web/.git"),
          "origin/main",
          "git@host:g/web.git",
          true);
  private final NewRepo api =
      new NewRepo(
          "api",
          new ProjectConfig("/repo/api", "origin/develop", "stage", List.of()),
          Path.of("/repo/api"),
          Path.of("/w/ABC-42-api"),
          Path.of("/repo/api/.git"),
          "origin/develop",
          "git@host:g/api.git",
          false);
  private final NewTask plainTask = NewTask.builder("ABC-42", "web").build();
  private final ConfigService.ConfigFile.AutoplanConfig autoplan =
      ConfigService.ConfigFile.AutoplanConfig.defaults();
  private final ConfigService.ConfigFile.VisualizeConfig visualize =
      ConfigService.ConfigFile.VisualizeConfig.defaults();
  private final ConfigService.ConfigFile.DiagnoseConfig diagnose =
      ConfigService.ConfigFile.DiagnoseConfig.defaults();

  @TempDir Path root;

  private SubAgentBriefing agent;

  @BeforeEach
  void wireAgent() {
    agent =
        new SubAgentBriefing(
            new SessionBrief(
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))),
            scenarios);
  }

  @Test
  void pointsTheAgentAtTheRequestTheTaskWasOpenedWithSinceLaterInstructionsReplaceTheContext()
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing = agent.of(plainTask, web, List.of(web), autoplan, visualize, diagnose);

    assertThat(briefing)
        .contains(
            "`task_request.md`, if present: the request the task opened with, never overwritten.");
  }

  @Test
  void namesLogicHardToFollowFromItsCodeAsWorthPictureWhenVisualizeIsOn() throws Exception {

    String briefing =
        agent.of(
            plainTask,
            web,
            List.of(web),
            autoplan,
            new ConfigService.ConfigFile.VisualizeConfig(true),
            diagnose);

    assertThat(briefing)
        .contains(
            "logic hard to follow from its code, such as rules that combine or rows and columns"
                + " that shift each other: draw a worked example, its input and its outcome");
  }

  @Test
  void grantsTheRebaseOntoTheMovedTargetWithLeaseAndStillForbidsForceAndMerge() throws Exception {

    String briefing = agent.of(plainTask, web, List.of(web), autoplan, visualize, diagnose);

    assertThat(briefing)
        .contains(
            "No `git merge` or `cherry-pick`",
            "`git rebase` this branch onto its request's target",
            "then `push --force-with-lease`",
            "(`--force`, `--amend`,");
  }

  @Test
  void addsNothingForContextSectionTheBriefLeavesEmpty() throws Exception {
    Path blank = Files.createDirectories(root.resolve("blank"));
    Path absent = Files.createDirectories(root.resolve("absent"));
    Files.createDirectories(blank.resolve("roles/session"));
    Files.writeString(blank.resolve("roles/session/brief.md"), "## context\n");
    Files.createDirectories(absent.resolve("roles/session"));
    Files.writeString(absent.resolve("roles/session/brief.md"), "## other\nText.\n");

    String withBlank =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(blank.toString()))),
                scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                web,
                List.of(web),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());
    String withNone =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(absent.toString()))),
                scenarios)
            .of(
                NewTask.builder("ABC-42", "web").build(),
                web,
                List.of(web),
                ConfigService.ConfigFile.AutoplanConfig.defaults(),
                ConfigService.ConfigFile.VisualizeConfig.defaults(),
                ConfigService.ConfigFile.DiagnoseConfig.defaults());

    assertThat(withBlank).isEqualTo(withNone);
  }

  @Test
  void namesTheDeployBranchAsJagtsAloneSoTheAgentNeverMergesIntoIt() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing = agent.of(plainTask, web, List.of(web), autoplan, visualize, diagnose);

    assertThat(briefing).contains("The deploy branch `dev` is jagt's `deploy` alone.");
  }

  @Test
  void listsTheDeployWorktreeConflictIsHandedOverInAsTheSessionsOwn() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing = agent.of(plainTask, web, List.of(web), autoplan, visualize, diagnose);

    assertThat(briefing)
        .contains(
            "- Also yours while jagt hands you a deploy conflict there: /repo/ABC-42-deploy.");
  }

  @Test
  void namesTheDeployBranchOfEveryRepositoryOfTheTask() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    NewRepo apiOnMain =
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
        agent.of(plainTask, web, List.of(web, apiOnMain), autoplan, visualize, diagnose);

    assertThat(briefing)
        .contains("The deploy branches `dev` (web), `stage` (api) are jagt's `deploy` alone.");
  }

  @Test
  void namesTheBaseBranchOfEveryRepositoryOfTheTaskReadOnly() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing = agent.of(plainTask, web, List.of(web, api), autoplan, visualize, diagnose);

    assertThat(briefing)
        .contains("The base branches `origin/main` (web), `origin/develop` (api) are read-only.");
  }

  @Test
  void saysNothingOfDeployBranchWhenTheTaskHasNone() throws Exception {
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

    String briefing = agent.of(plainTask, repo, List.of(repo), autoplan, visualize, diagnose);

    assertThat(briefing).doesNotContain("deploy branch");
  }

  @Test
  void asksForPlanFirstWhenAutoplanIsOnNamingTheFileCount() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing =
        agent.of(
            plainTask,
            web,
            List.of(web),
            autoplan.withEnabled(true).withFiles(12),
            visualize,
            diagnose);

    assertThat(briefing)
        .contains("plan first if the task changes more than 12 files")
        .contains("report IN_PROGRESS with outcome `no_plan`");
  }

  @Test
  void tellsTheSessionToPlanFirstWhenTheTaskWasOpenedInPlanMode() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing =
        agent.of(
            NewTask.builder("ABC-42", "web").mode("plan").build(),
            web,
            List.of(web),
            autoplan,
            visualize,
            diagnose);

    assertThat(briefing).contains("PLAN FIRST: the human asked for a plan.");
  }

  @Test
  void saysNothingOfPlanningFirstWhenTheTaskWasOpenedInAutoMode() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing =
        agent.of(
            NewTask.builder("ABC-42", "web").mode("auto").build(),
            web,
            List.of(web),
            autoplan,
            visualize,
            diagnose);

    assertThat(briefing).doesNotContain("PLAN FIRST");
  }

  @Test
  void saysNothingOfAutoplanWhenItIsLeftAtItsDefault() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing = agent.of(plainTask, web, List.of(web), autoplan, visualize, diagnose);

    assertThat(briefing).doesNotContain("AUTOPLAN");
  }

  @Test
  void namesTheOtherWorktreesOfTaskAndNotTheOneTheAgentRunsIn() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing = agent.of(plainTask, web, List.of(web, api), autoplan, visualize, diagnose);

    assertThat(briefing)
        .contains("- This task ALSO works in these worktrees")
        .contains("  - api: /w/ABC-42-api (cut from origin/develop)")
        .doesNotContain("  - web:");
  }

  @Test
  void saysTaskWorksInOneRepositoryOnlyWhereItHasNoOther() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing = agent.of(plainTask, web, List.of(web), autoplan, visualize, diagnose);

    assertThat(briefing)
        .contains("- This task works in this repository only.")
        .doesNotContain("ALSO works");
  }

  @Test
  void asksForTaskVisualMdWhenVisualizeIsOn() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing =
        agent.of(plainTask, web, List.of(web), autoplan, visualize.withEnabled(true), diagnose);

    assertThat(briefing)
        .contains(
            "write `task_visual.md` in the worktree as part of the plan when you plan first, else"
                + " before your first edit")
        .contains("A two-line bug fix is not.");
  }

  @Test
  void tablesAndCallsBetweenServersAreAlwaysWorthPictureWhenVisualizeIsOn() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing =
        agent.of(plainTask, web, List.of(web), autoplan, visualize.withEnabled(true), diagnose);

    assertThat(briefing)
        .contains(
            "A new or changed table, or a call between servers, services or a database, always is,")
        .contains(
            "an ER diagram draws every reference between the tables, a sequence diagram every"
                + " call");
  }

  @Test
  void warnsThatSemicolonEndsMermaidStatementWhenVisualizeIsOn() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing =
        agent.of(plainTask, web, List.of(web), autoplan, visualize.withEnabled(true), diagnose);

    assertThat(briefing).contains("no `;` in a Mermaid label, where it ends the statement");
  }

  @Test
  void saysNothingOfPictureWhenVisualizeIsLeftAtItsDefault() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing = agent.of(plainTask, web, List.of(web), autoplan, visualize, diagnose);

    assertThat(briefing).doesNotContain("VISUALIZE").doesNotContain("task_visual.md");
  }

  @Test
  void asksForFactsAndControlBeforeCauseWhenDiagnoseIsOn() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing =
        agent.of(plainTask, web, List.of(web), autoplan, visualize, diagnose.withEnabled(true));

    assertThat(briefing)
        .contains("DIAGNOSE: a bug is diagnosed from facts before code.")
        .contains("A repro counts only when it shows the report's signal.");
  }

  @Test
  void saysNothingOfDiagnosingWhenDiagnoseIsLeftAtItsDefault() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing = agent.of(plainTask, web, List.of(web), autoplan, visualize, diagnose);

    assertThat(briefing).doesNotContain("DIAGNOSE");
  }

  @Test
  void keepsTheBranchRuleWhereTheHumansCopyDropsIt() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(
        root.resolve("roles/session/brief.md"), "## context\n\n<rules>\n1. Work.\n</rules>\n");

    String briefing = agent.of(plainTask, web, List.of(web), autoplan, visualize, diagnose);

    assertThat(briefing).contains("1. Work.", "<hard_safety>", "push only to `ABC-42`");
  }

  @Test
  void asksForTheJudgementOfEverySwitchThatIsOnInTheFirstReportAndThePlanHandBack()
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String briefing =
        agent.of(
            plainTask,
            web,
            List.of(web),
            autoplan,
            new ConfigService.ConfigFile.VisualizeConfig(true),
            diagnose);

    assertThat(briefing)
        .contains(
            "first status report and the one handing back a plan carry `switches`: for each of"
                + " visualize");
  }

  @Test
  void tellsTaskWithNoRepositoryToNameEveryProjectItChangesInItsPlan() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));
    String briefing =
        new SubAgentBriefing(
                new SessionBrief(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))),
                scenarios)
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
  void endsTheBriefingWithTheScenariosLineWhenThereIsOne() throws Exception {
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
  void endsTheScratchBriefingWithTheScenariosLineWhenThereIsOne() throws Exception {
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
