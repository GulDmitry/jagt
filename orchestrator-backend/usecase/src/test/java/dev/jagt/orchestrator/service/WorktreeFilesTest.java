package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.EditorDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorktreeFilesTest {

    @Test
    void copiesTheEditorsFilesAndDirectoriesIntoTheWorktree(@TempDir Path root) throws Exception {
        Path project = root.resolve("repo");
        Files.createDirectories(project.resolve(".editor").resolve("runs"));
        Files.writeString(project.resolve(".editor").resolve("runs").resolve("App.xml"), "<configuration/>");
        Files.writeString(project.resolve(".editor").resolve("db.xml"), "<db/>");
        Path worktree = root.resolve("ABC-1-repo");

        WorktreeFiles.copyProjectFiles(project, worktree, List.of(".editor/runs", ".editor/db.xml"));

        assertThat(worktree.resolve(".editor").resolve("runs").resolve("App.xml")).hasContent("<configuration/>");
        assertThat(worktree.resolve(".editor").resolve("db.xml")).hasContent("<db/>");
    }

    @Test
    void doesNotFailWhenTheProjectHasNoneOfTheEditorsFiles(@TempDir Path root) {
        Path project = root.resolve("repo");
        Path worktree = root.resolve("ABC-1-repo");

        WorktreeFiles.copyProjectFiles(project, worktree, List.of(".editor/runs"));

        assertThat(worktree.resolve(".editor")).doesNotExist();
    }

    @Test
    void copiesNoLocalFileOutOfWhatTheEditorWrote(@TempDir Path root) throws Exception {
        Path base = root.resolve("base");
        Files.createDirectories(base.resolve(".editor"));
        Files.writeString(base.resolve(".editor/.env"), "IGNORED=1");
        Path wt = root.resolve("wt");
        Files.createDirectories(wt);

        WorktreeFiles.copyLocalFiles(base, wt, List.of("**/.env"), Set.of(".editor"));

        assertThat(wt.resolve(".editor/.env")).doesNotExist();
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

        WorktreeFiles.copyLocalFiles(base, wt, List.of("**/.env", "**/*.pem"), Set.of());

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

        WorktreeFiles.copyLocalFiles(base, wt, List.of("**/.env"), Set.of());

        assertThat(wt.resolve(".env")).hasContent("AS COMMITTED");
    }

    @Test
    void copiesTheEnvFileASingleModuleRepositoryKeepsAtItsRoot(@TempDir Path root) throws Exception {
        Path base = root.resolve("base");
        Files.createDirectories(base);
        Files.writeString(base.resolve(".env"), "SECRET=1");
        Path wt = root.resolve("wt");
        Files.createDirectories(wt);

        WorktreeFiles.copyLocalFiles(base, wt, List.of("**/.env"), Set.of());

        assertThat(wt.resolve(".env")).exists().hasContent("SECRET=1");
    }

    @Test
    void copiesNothingWithoutFailingWhenAGlobsDirectoryIsAbsent(@TempDir Path root) throws Exception {
        Path base = root.resolve("base");
        Files.createDirectories(base.resolve("src"));
        Files.writeString(base.resolve("src/Main.java"), "class Main {}");
        Path wt = root.resolve("wt");
        Files.createDirectories(wt);

        WorktreeFiles.copyLocalFiles(base, wt, List.of("vendor/**"), Set.of());

        assertThat(wt.resolve("vendor")).doesNotExist();
    }

    @Test
    void keepsJagtsOwnPlumbingOutOfEveryWorktreesGitStatus(@TempDir Path gitCommonDir) throws Exception {
        Files.createDirectories(gitCommonDir.resolve("info"));
        Files.writeString(gitCommonDir.resolve("info").resolve("exclude"), "*.local\n");

        WorktreeFiles.excludeOrchestratorPlumbing(gitCommonDir, mock(AgentRuntime.class), mock(EditorDriver.class));

        assertThat(Files.readString(gitCommonDir.resolve("info").resolve("exclude")))
                .contains("*.local", "task_context.md", "plan.md", "master-review.md", "AGENTS.md", ".jagt/");
    }

    @Test
    void keepsTheActiveAgentsOwnFilesOutOfEveryWorktreesGitStatus(@TempDir Path gitCommonDir) throws Exception {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.statusExclusions()).thenReturn(List.of(".acme.json", ".acme/"));

        WorktreeFiles.excludeOrchestratorPlumbing(gitCommonDir, runtime, mock(EditorDriver.class));

        assertThat(Files.readString(gitCommonDir.resolve("info").resolve("exclude")))
                .contains(".acme.json", ".acme/");
    }

    @Test
    void keepsTheEditorsCopiedFilesOutOfEveryWorktreesGitStatus(@TempDir Path gitCommonDir) throws Exception {
        EditorDriver editor = mock(EditorDriver.class);
        when(editor.projectFiles()).thenReturn(List.of(".editor/runs"));

        WorktreeFiles.excludeOrchestratorPlumbing(gitCommonDir, mock(AgentRuntime.class), editor);

        assertThat(Files.readString(gitCommonDir.resolve("info").resolve("exclude"))).contains(".editor/runs");
    }

    @Test
    void addsNothingTwiceWhenTheProjectIsInitialisedAgain(@TempDir Path gitCommonDir) throws Exception {
        WorktreeFiles.excludeOrchestratorPlumbing(gitCommonDir, mock(AgentRuntime.class), mock(EditorDriver.class));
        WorktreeFiles.excludeOrchestratorPlumbing(gitCommonDir, mock(AgentRuntime.class), mock(EditorDriver.class));

        assertThat(Files.readString(gitCommonDir.resolve("info").resolve("exclude")))
                .containsOnlyOnce("task_context.md");
    }

    @Test
    void countsTheActiveAgentsOwnConfigAsGeneratedRatherThanTheAgentsWork() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.generatedFiles()).thenReturn(List.of(".acme.json"));

        assertThat(WorktreeFiles.generated(runtime)).contains(".acme.json", "task_context.md");
    }
}
