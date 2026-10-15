package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class FinishedArtifactsTest {

    @Test
    void keepsTheDocumentsOfAFinishedTaskWhereAHumanCanStillReadThem(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-42-proj");
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve("plan.md"), "the plan");
        Files.writeString(worktree.resolve("review_replies.md"), "the replies");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", worktree.toString(), TaskStatus.APPROVED)
                .alias("a1").title("a thing").build());

        new FinishedArtifacts(state, paths).keep("ABC-42");

        Path kept = Files.list(root.resolve("artifacts")).findFirst().orElseThrow();
        assertThat(kept.getFileName().toString()).endsWith("-ABC-42");
        assertThat(kept.resolve("plan.md")).hasContent("the plan");
        assertThat(kept.resolve("review_replies.md")).hasContent("the replies");
    }

    @Test
    void writesNoDirectoryForATaskThatLeftNoDocumentBehind(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-42-proj");
        Files.createDirectories(worktree);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", worktree.toString(), TaskStatus.APPROVED)
                .alias("a1").title("a thing").build());

        new FinishedArtifacts(state, paths).keep("ABC-42");

        assertThat(root.resolve("artifacts")).doesNotExist();
    }

    @Test
    void keepsALineNamingADocumentPastTheSizeLimitInsteadOfTheDocument(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-42-proj");
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve("plan.md"), "too long a plan");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", worktree.toString(), TaskStatus.APPROVED)
                .alias("a1").title("a thing").build());

        new FinishedArtifacts(state, paths, 1_000, 10).keep("ABC-42");

        Path kept = Files.list(root.resolve("artifacts")).findFirst().orElseThrow();
        assertThat(kept.resolve("plan.md")).doesNotExist();
        assertThat(kept.resolve("plan.md.not-kept")).hasContent("plan.md was 15 bytes, past the 10 kept");
    }

    @Test
    void zipsTheOlderHalfOfTheTasksOnceTheStoreIsFull(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("artifacts/20260101-ABC-1"));
        Files.writeString(root.resolve("artifacts/20260101-ABC-1/plan.md"), "the first plan");
        Files.createDirectories(root.resolve("artifacts/20260102-ABC-2"));
        Files.createDirectories(root.resolve("artifacts/20260103-ABC-3"));
        Path worktree = root.resolve("ABC-42-proj");
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve("plan.md"), "the plan");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", worktree.toString(), TaskStatus.APPROVED)
                .alias("a1").title("a thing").build());

        new FinishedArtifacts(state, paths, 4, 1_000).keep("ABC-42");

        Path archive = Files.list(root.resolve("artifacts"))
                .filter(path -> path.toString().endsWith(".zip")).findFirst().orElseThrow();
        assertThat(root.resolve("artifacts/20260101-ABC-1")).doesNotExist();
        assertThat(root.resolve("artifacts/20260102-ABC-2")).doesNotExist();
        assertThat(root.resolve("artifacts/20260103-ABC-3")).exists();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertThat(new String(zip.getInputStream(zip.getEntry("20260101-ABC-1/plan.md")).readAllBytes()))
                    .isEqualTo("the first plan");
        }
    }
}
