package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
