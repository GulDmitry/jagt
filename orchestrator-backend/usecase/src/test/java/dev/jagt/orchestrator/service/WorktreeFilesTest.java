package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorktreeFilesTest {

    @Test
    void copiesLegacyIdeaRunConfigurationsIntoTheWorktree(@TempDir Path root) throws Exception {
        Path project = root.resolve("repo");
        Files.createDirectories(project.resolve(".idea").resolve("runConfigurations"));
        Files.writeString(project.resolve(".idea").resolve("runConfigurations").resolve("App.xml"),
                "<configuration/>");
        Path worktree = root.resolve("ABC-1-repo");

        WorktreeFiles.copyIdeProjectFiles(project, worktree);

        assertThat(worktree.resolve(".idea").resolve("runConfigurations").resolve("App.xml"))
                .exists().hasContent("<configuration/>");
    }

    @Test
    void copiesDatabaseConnectionsIntoTheWorktree(@TempDir Path root) throws Exception {
        Path project = root.resolve("repo");
        Files.createDirectories(project.resolve(".idea").resolve("dataSources"));
        Files.writeString(project.resolve(".idea").resolve("dataSources.xml"), "<dataSource/>");
        Files.writeString(project.resolve(".idea").resolve("dataSources.local.xml"), "<local/>");
        Files.writeString(project.resolve(".idea").resolve("dataSources").resolve("pg.xml"), "<db/>");
        Path worktree = root.resolve("ABC-1-repo");

        WorktreeFiles.copyIdeProjectFiles(project, worktree);

        assertThat(worktree.resolve(".idea").resolve("dataSources.xml")).exists().hasContent("<dataSource/>");
        assertThat(worktree.resolve(".idea").resolve("dataSources.local.xml")).exists().hasContent("<local/>");
        assertThat(worktree.resolve(".idea").resolve("dataSources").resolve("pg.xml")).exists().hasContent("<db/>");
    }

    @Test
    void copiesModernDotRunConfigurationsIntoTheWorktree(@TempDir Path root) throws Exception {
        Path project = root.resolve("repo");
        Files.createDirectories(project.resolve(".run"));
        Files.writeString(project.resolve(".run").resolve("App.run.xml"), "<configuration/>");
        Path worktree = root.resolve("ABC-1-repo");

        WorktreeFiles.copyIdeProjectFiles(project, worktree);

        assertThat(worktree.resolve(".run").resolve("App.run.xml")).exists().hasContent("<configuration/>");
    }

    @Test
    void doesNotFailWhenBaseProjectHasNoSharedRunConfigurations(@TempDir Path root) {
        Path project = root.resolve("repo");
        Path worktree = root.resolve("ABC-1-repo");

        WorktreeFiles.copyIdeProjectFiles(project, worktree);

        assertThat(worktree.resolve(".idea")).doesNotExist();
    }

    @Test
    void copiesLocalFilesMatchingGlobsSkippingHeavyDirs(@TempDir Path root) throws Exception {
        Path base = root.resolve("base");
        Files.createDirectories(base.resolve("app"));
        Files.writeString(base.resolve("app/.env"), "SECRET=1");
        Files.createDirectories(base.resolve("lib"));
        Files.writeString(base.resolve("lib/key.pem"), "PEM");
        Files.createDirectories(base.resolve("node_modules"));
        Files.writeString(base.resolve("node_modules/.env"), "IGNORED=1");
        Path wt = root.resolve("wt");
        Files.createDirectories(wt);

        WorktreeFiles.copyLocalFiles(base, wt, List.of("**/.env", "**/*.pem"));

        assertThat(wt.resolve("app/.env")).exists().hasContent("SECRET=1");
        assertThat(wt.resolve("lib/key.pem")).exists().hasContent("PEM");
        assertThat(wt.resolve("node_modules/.env")).doesNotExist();
    }

    @Test
    void leavesAFileTheCheckoutAlreadyProvidedAsGitWroteIt(@TempDir Path root) throws Exception {
        Path base = root.resolve("base");
        Files.createDirectories(base);
        Files.writeString(base.resolve(".env"), "LOCALLY EDITED");
        Path wt = root.resolve("wt");
        Files.createDirectories(wt);
        Files.writeString(wt.resolve(".env"), "AS COMMITTED");

        WorktreeFiles.copyLocalFiles(base, wt, List.of("**/.env"));

        assertThat(wt.resolve(".env")).hasContent("AS COMMITTED");
    }

    @Test
    void copiesTheEnvFileASingleModuleRepositoryKeepsAtItsRoot(@TempDir Path root) throws Exception {
        Path base = root.resolve("base");
        Files.createDirectories(base);
        Files.writeString(base.resolve(".env"), "SECRET=1");
        Path wt = root.resolve("wt");
        Files.createDirectories(wt);

        WorktreeFiles.copyLocalFiles(base, wt, List.of("**/.env"));

        assertThat(wt.resolve(".env")).exists().hasContent("SECRET=1");
    }

    @Test
    void copiesNothingWithoutFailingWhenAGlobsDirectoryIsAbsent(@TempDir Path root) throws Exception {
        Path base = root.resolve("base");
        Files.createDirectories(base.resolve("src"));
        Files.writeString(base.resolve("src/Main.java"), "class Main {}");
        Path wt = root.resolve("wt");
        Files.createDirectories(wt);

        WorktreeFiles.copyLocalFiles(base, wt, List.of("vendor/**"));

        assertThat(wt.resolve("vendor")).doesNotExist();
    }

    @Test
    void keepsJagtsOwnPlumbingOutOfEveryWorktreesGitStatus(@TempDir Path gitCommonDir) throws Exception {
        Files.createDirectories(gitCommonDir.resolve("info"));
        Files.writeString(gitCommonDir.resolve("info").resolve("exclude"), "*.local\n");

        WorktreeFiles.excludeOrchestratorPlumbing(gitCommonDir);

        assertThat(Files.readString(gitCommonDir.resolve("info").resolve("exclude")))
                .contains("*.local", "task_context.md", "AGENTS.md", ".claude/");
    }

    @Test
    void addsNothingTwiceWhenTheProjectIsInitialisedAgain(@TempDir Path gitCommonDir) throws Exception {
        WorktreeFiles.excludeOrchestratorPlumbing(gitCommonDir);
        WorktreeFiles.excludeOrchestratorPlumbing(gitCommonDir);

        assertThat(Files.readString(gitCommonDir.resolve("info").resolve("exclude")))
                .containsOnlyOnce("task_context.md");
    }
}
