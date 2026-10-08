package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FinishedArtifactsTest {

    private final Notifications notifications = mock(Notifications.class);

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

        new FinishedArtifacts(state, notifications, paths).keep("ABC-42");

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

        new FinishedArtifacts(state, notifications, paths).keep("ABC-42");

        assertThat(root.resolve("artifacts")).doesNotExist();
    }

    @Test
    void keepsADocumentPastTheSizeLimitAndSaysSoRatherThanLosingIt(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-42-proj");
        Files.createDirectories(worktree);
        Files.write(worktree.resolve("plan.md"), new byte[(int) FinishedArtifacts.MAX_ARTIFACT_BYTES + 1]);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", worktree.toString(), TaskStatus.APPROVED)
                .alias("a1").title("a thing").build());

        new FinishedArtifacts(state, notifications, paths).keep("ABC-42");

        Path kept = Files.list(root.resolve("artifacts")).findFirst().orElseThrow();
        assertThat(kept.resolve("plan.md")).exists();
        verify(notifications).send(any(Notification.class));
    }

    @Test
    void saysNothingWhileTheStoreIsStillSmall(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-42-proj");
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve("plan.md"), "the plan");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", worktree.toString(), TaskStatus.APPROVED)
                .alias("a1").title("a thing").build());

        new FinishedArtifacts(state, notifications, paths).keep("ABC-42");

        verify(notifications, never()).send(any(Notification.class));
    }

    @Test
    void asksForAPruneOnceTheStoreHoldsMoreTasksThanAnyoneWillRead(@TempDir Path root) throws Exception {
        for (int i = 0; i < FinishedArtifacts.MAX_DIRECTORIES; i++) {
            Files.createDirectories(root.resolve("artifacts").resolve("20260101-ABC-" + i));
        }
        Path worktree = root.resolve("ABC-42-proj");
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve("plan.md"), "the plan");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", worktree.toString(), TaskStatus.APPROVED)
                .alias("a1").title("a thing").build());

        new FinishedArtifacts(state, notifications, paths).keep("ABC-42");

        assertThat(Files.list(root.resolve("artifacts")))
                .anySatisfy(kept -> assertThat(kept.getFileName().toString()).endsWith("-ABC-42"));
        verify(notifications).send(any(Notification.class));
    }
}
