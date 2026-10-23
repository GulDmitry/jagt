package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubAgentBriefingTest {

    @Test
    void pointsTheAgentAtTheRequestTheTaskWasOpenedWithSinceLaterInstructionsReplaceTheContext(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).contains("`task_request.md`, if present: the request the task opened with, never overwritten.");
    }

    @Test
    void namesTheDeployBranchAsJagtsAloneSoTheAgentNeverMergesIntoIt(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).contains("The deploy branch `dev` is jagt's `deploy` alone.");
    }

    @Test
    void listsTheDeployWorktreeAConflictIsHandedOverInAsTheSessionsOwn(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).contains("- Also yours while jagt hands you a deploy conflict there: /repo/ABC-42-deploy.");
    }

    @Test
    void namesTheDeployBranchOfEveryRepositoryOfTheTask(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo web = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);
        NewRepo api = new NewRepo("api", new ProjectConfig("/repo/api", "origin/main", "stage", List.of()),
                Path.of("/repo/api"), Path.of("/w/ABC-42-api"), Path.of("/repo/api/.git"), "origin/main",
                "git@host:g/api.git", false);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), web, List.of(web, api),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).contains("The deploy branches `dev` (web), `stage` (api) are jagt's `deploy` alone.");
    }

    @Test
    void namesTheBaseBranchOfEveryRepositoryOfTheTaskReadOnly(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo web = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);
        NewRepo api = new NewRepo("api", new ProjectConfig("/repo/api", "origin/develop", "stage", List.of()),
                Path.of("/repo/api"), Path.of("/w/ABC-42-api"), Path.of("/repo/api/.git"), "origin/develop",
                "git@host:g/api.git", false);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), web, List.of(web, api),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).contains("The base branches `origin/main` (web), `origin/develop` (api) are read-only.");
    }

    @Test
    void saysNothingOfADeployBranchWhenTheTaskHasNone(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", null, List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).doesNotContain("deploy branch");
    }

    @Test
    void asksForAPlanFirstWhenAutoplanIsOnNamingTheFileCount(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults().withEnabled(true).withFiles(12),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).contains("plan first if the task changes more than 12 files")
                .contains("report IN_PROGRESS with outcome `no_plan`");
    }

    @Test
    void tellsTheSessionToPlanFirstWhenTheTaskWasOpenedInPlanMode(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").mode("plan").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).contains("PLAN FIRST: the human asked for a plan.");
    }

    @Test
    void saysNothingOfPlanningFirstWhenTheTaskWasOpenedInAutoMode(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").mode("auto").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).doesNotContain("PLAN FIRST");
    }

    @Test
    void saysNothingOfAutoplanWhenItIsLeftAtItsDefault(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).doesNotContain("AUTOPLAN");
    }

    @Test
    void namesTheOtherWorktreesOfATaskAndNotTheOneTheAgentRunsIn(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo web = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);
        NewRepo api = new NewRepo("api", new ProjectConfig("/repo/api", "origin/develop", "stage", List.of()),
                Path.of("/repo/api"), Path.of("/w/ABC-42-api"), Path.of("/repo/api/.git"), "origin/develop",
                "git@host:g/api.git", false);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), web, List.of(web, api),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).contains("- This task ALSO works in these worktrees")
                .contains("  - api: /w/ABC-42-api (cut from origin/develop)")
                .doesNotContain("  - web:");
    }

    @Test
    void saysATaskWorksInOneRepositoryOnlyWhereItHasNoOther(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo web = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), web, List.of(web),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).contains("- This task works in this repository only.").doesNotContain("ALSO works");
    }

    @Test
    void asksForTaskVisualMdWhenVisualizeIsOn(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults().withEnabled(true),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).contains("write `task_visual.md` in the worktree as part of the plan when you plan first, else before your first edit")
                .contains("A two-line bug fix is not.");
    }

    @Test
    void warnsThatASemicolonEndsAMermaidStatementWhenVisualizeIsOn(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults().withEnabled(true),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).contains("no `;` in a Mermaid label, where it ends the statement");
    }

    @Test
    void saysNothingOfAPictureWhenVisualizeIsLeftAtItsDefault(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).doesNotContain("VISUALIZE").doesNotContain("task_visual.md");
    }

    @Test
    void asksForFactsAndAControlBeforeACauseWhenDiagnoseIsOn(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults().withEnabled(true));

        assertThat(briefing).contains("DIAGNOSE: a bug is diagnosed from facts before code.")
                .contains("A repro counts only when it shows the report's signal.");
    }

    @Test
    void saysNothingOfDiagnosingWhenDiagnoseIsLeftAtItsDefault(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(),
                        ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).doesNotContain("DIAGNOSE");
    }

    @Test
    void keepsTheBranchRuleWhereTheHumansCopyDropsIt(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("session-brief.md"), "## context\n\n<rules>\n1. Work.\n</rules>\n");
        NewRepo repo = new NewRepo("web", new ProjectConfig("/repo/web", "origin/main", "dev", List.of()),
                Path.of("/repo/web"), Path.of("/w/ABC-42"), Path.of("/repo/web/.git"), "origin/main",
                "git@host:g/web.git", true);

        String briefing = new SubAgentBriefing(
                new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()))))
                .of(NewTask.builder("ABC-42", "web").build(), repo, List.of(repo),
                        ConfigService.ConfigFile.AutoplanConfig.defaults(), ConfigService.ConfigFile.VisualizeConfig.defaults(),
                        ConfigService.ConfigFile.DiagnoseConfig.defaults());

        assertThat(briefing).contains("1. Work.", "<hard_safety>", "push only to `ABC-42`");
    }
}
