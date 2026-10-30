package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.NotesDue;
import dev.jagt.orchestrator.service.TurnTree;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TurnEndsTest {

    private static final long NOW = 10_000_000_000L;

    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final ConfigService config = mock(ConfigService.class);
    private final TurnTree turnTree = new TurnTree(null, null);
    private final NotesDue notes =new NotesDue(Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));

    @Test
    void sendsOnATurnThatEndsWithOnlyAReadCallSinceItBegan() {
        when(runtime.refusedTurnEnd(contains("update_agent_status"))).thenReturn("{\"decision\": \"block\"}");
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(3_000L).session(Session.defaults().withWorkStartedAt(2_000L)).build();

        String answered = new TurnEnds(runtime, config, notes, turnTree).answer("ABC-1", task, false, false);

        assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
    }

    @Test
    void letsATurnEndWhoseStartIsUnknown() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();

        String answered = new TurnEnds(runtime, config, notes, turnTree).answer("ABC-1", task, false, false);

        assertThat(answered).isEmpty();
    }

    @Test
    void sendsOnATurnThatEndsOnAQuestionTheHumanAnsweredToStartIt() {
        when(runtime.refusedTurnEnd(contains("update_agent_status"))).thenReturn("{\"decision\": \"block\"}");
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("keep v2?").report(AgentReport.QUESTION).session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(0L)).build();

        String answered = new TurnEnds(runtime, config, notes, turnTree).answer("ABC-1", task, false, false);

        assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
    }

    @ParameterizedTest
    @ValueSource(longs = {2_000L, 3_000L})
    void letsATurnEndThatReportedSinceItBegan(long reportedAt) {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(reportedAt)).build();

        String answered = new TurnEnds(runtime, config, notes, turnTree).answer("ABC-1", task, false, false);

        assertThat(answered).isEmpty();
    }

    @Test
    void tellsTheHumanARoundHandedBackIsWithTheMaster() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(runtime.toldTheHuman("→ with the Master for review · its verdict arrives here"))
                .thenReturn("{\"systemMessage\": \"with the Master\"}");
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1")
                .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L)).build();

        String answered = new TurnEnds(runtime, config, notes, turnTree).answer("ABC-1", task, false, false);

        assertThat(answered).isEqualTo("{\"systemMessage\": \"with the Master\"}");
    }

    @Test
    void saysNothingAtAHandedBackRoundNoMasterReads() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1")
                .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L)).build();

        String answered = new TurnEnds(runtime, config, notes, turnTree).answer("ABC-1", task, false, false);

        assertThat(answered).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true"})
    void letsATurnEndThatWasAlreadySentOnOrWaitsOnBackgroundWork(boolean sentOn, boolean paused) {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(0L)).build();

        String answered = new TurnEnds(runtime, config, notes, turnTree).answer("ABC-1", task, sentOn, paused);

        assertThat(answered).isEmpty();
    }

    @Test
    void tellsTheHumanARoundBeingVerifiedIsVerifiedBeforeTheMasterReadsIt() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(runtime.toldTheHuman("→ verification runs first · the Master reads the round once it passes"))
                .thenReturn("{\"systemMessage\": \"verification first\"}");
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).alias("a1")
                .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L)).build();

        String answered = new TurnEnds(runtime, config, notes, turnTree).answer("ABC-1", task, false, false);

        assertThat(answered).isEqualTo("{\"systemMessage\": \"verification first\"}");
    }

    @Test
    void sendsOnATurnEndOnceWhenTheNotesHaveFallenBehindTheWork(@TempDir Path worktree) throws Exception {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("task_notes.md"), "old"),
                FileTime.fromMillis(NOW - 3_600_000L));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(runtime.refusedTurnEnd(contains("rewrite task_notes.md"))).thenReturn("{\"decision\": \"block\"}");
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).alias("a1")
                .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L)).build();

        String answered = new TurnEnds(runtime, config, notes, turnTree).answer("ABC-1", task, false, false);

        assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
    }

    @Test
    void neverAsksForTheNotesAgainOnATurnEndTheFirstRefusalSentOn(@TempDir Path worktree) throws Exception {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("task_notes.md"), "old"),
                FileTime.fromMillis(NOW - 3_600_000L));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).alias("a1")
                .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L)).build();

        String answered = new TurnEnds(runtime, config, notes, turnTree).answer("ABC-1", task, true, false);

        assertThat(answered).isEmpty();
    }
}
