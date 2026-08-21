package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewRepliesReportTest {

    private final ConfigService config = mock(ConfigService.class);

    @Test
    void showsEachCommentWithTheVerdictAndTheReplyThatWillBeSentForIt(@TempDir Path root) throws IOException {
        Path worktree = Files.createDirectories(root.resolve("wt"));
        Files.writeString(worktree.resolve("review_replies.md"), """
                ## !12 thread 1
                > extract the grid cases into a separate parameterized test
                NO CHANGE - buildsTheGrid already is one, and its cases live in grids().

                ## src/Grid.java:56
                > the canonical row count is wrong
                FIXED - Measured it and pinned the count.
                """);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").build());

        String out = new ReviewRepliesReport(new TaskViews(state, config), state).render("a1");

        assertThat(out).contains(
                "  1 · NO CHANGE · !12 thread 1",
                "      > extract the grid cases into a separate parameterized test",
                "      buildsTheGrid already is one, and its cases live in grids().",
                "  2 · FIXED · src/Grid.java:56");
    }

    @Test
    void keepsWhatTheAgentWroteOutsideThePrescribedShape(@TempDir Path root) throws IOException {
        Path worktree = Files.createDirectories(root.resolve("wt"));
        Files.writeString(worktree.resolve("review_replies.md"),
                "the bot reviewed a stale diff of that file\n\n## thread 1\nFIXED - Renamed it.\n");
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").build());

        String out = new ReviewRepliesReport(new TaskViews(state, config), state).render("a1");

        assertThat(out).contains("the bot reviewed a stale diff of that file");
    }

    @Test
    void marksABlockWhoseVerdictTheAgentLeftOutRatherThanDroppingIt(@TempDir Path root) throws IOException {
        Path worktree = Files.createDirectories(root.resolve("wt"));
        Files.writeString(worktree.resolve("review_replies.md"),
                "## thread 1\n> the canonical row count is wrong\nMeasured it two rounds ago.\n");
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").build());

        String out = new ReviewRepliesReport(new TaskViews(state, config), state).render("a1");

        assertThat(out).contains("  1 · no verdict · thread 1", "      Measured it two rounds ago.");
    }

    @Test
    void saysNothingIsPostedUntilTheRoundIsShipped(@TempDir Path root) throws IOException {
        Path worktree = Files.createDirectories(root.resolve("wt"));
        Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());

        String out = new ReviewRepliesReport(new TaskViews(state, config), state).render("a1");

        assertThat(out).startsWith(
                "review replies drafted for a1 · Widget layout is off — nothing is posted until `ship a1`");
    }

    @Test
    void saysSoWhenTheNamedTaskHasDraftedNothing(@TempDir Path root) {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("wt").toString(),
                TaskStatus.REVIEW_PENDING).alias("a1").build());

        String out = new ReviewRepliesReport(new TaskViews(state, config), state).render("a1");

        assertThat(out).isEqualTo("a1 has no drafted replies — review_replies.md is not in its worktree.");
    }

    @Test
    void refusesATaskItDoesNotKnow(@TempDir Path root) {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));

        String out = new ReviewRepliesReport(new TaskViews(state, config), state).render("zz");

        assertThat(out).isEqualTo("no task `zz`.");
    }

    @Test
    void namedNoTaskItShowsEveryRoundThatIsHoldingDrafts(@TempDir Path root) throws IOException {
        Path drafting = Files.createDirectories(root.resolve("wt1"));
        Files.writeString(drafting.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("alpha", drafting.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").build());
        state.putTask("ABC-2", TaskState.builder("alpha", root.resolve("wt2").toString(),
                TaskStatus.IN_PROGRESS).alias("a2").build());

        String out = new ReviewRepliesReport(new TaskViews(state, config), state).render("");

        assertThat(out).contains("review replies drafted for a1").doesNotContain("a2");
    }

    @Test
    void saysWhenNoRoundAnywhereHasDraftedAnything(@TempDir Path root) {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("alpha", root.resolve("wt").toString(),
                TaskStatus.REVIEW_PENDING).alias("a1").build());

        String out = new ReviewRepliesReport(new TaskViews(state, config), state).render(null);

        assertThat(out).isEqualTo("no drafted review replies: no task is carrying a review_replies.md.");
    }
}
