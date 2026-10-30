package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.UsageLimitsProperties;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.port.SubscriptionUsage;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SessionWakesTest {

    private static final Instant STOPPED = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant RESET = Instant.parse("2026-10-19T15:00:00Z");

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static UsageLimits reading(double percent, Optional<Instant> resetsAt) {
        SubscriptionUsage usage = () -> new SubscriptionUsage.Read(new SubscriptionUsage.Snapshot(
                List.of(new SubscriptionUsage.Window("5h", percent, resetsAt)), Optional.empty()));
        return new UsageLimits(Optional.of(usage));
    }

    private static SessionWakes sessionWakes(Path file, Instant now) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(file.getParent().toString()).withStateFile(file.toString())));
        return new SessionWakes(state, new TaskTurns(state, at(now)), at(now));
    }

    private static UsageLimitWakes wakes(UsageLimits limits, boolean on, Path file, Instant now) {
        return new UsageLimitWakes(limits, UsageLimitsProperties.defaults().withWakeOnReset(on),
                sessionWakes(file, now), at(now));
    }

    @Test
    void wakesNothingBeforeTheWindowResets(@TempDir Path dir) {
        Path file = dir.resolve("state.json");
        new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(dir.toString()).withStateFile(file.toString())))
                .putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");

        List<SessionWakes.Due> due = sessionWakes(file, RESET.minusSeconds(1)).takeDue(taskId -> false);

        assertThat(due).isEmpty();
    }

    @Test
    void wakesTheSessionOnceWhenTheWindowResets(@TempDir Path dir) {
        Path file = dir.resolve("state.json");
        new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(dir.toString()).withStateFile(file.toString())))
                .putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");
        SessionWakes later = sessionWakes(file, RESET);

        List<SessionWakes.Due> first = later.takeDue(taskId -> false);
        List<SessionWakes.Due> second = later.takeDue(taskId -> false);

        assertThat(first).containsExactly(new SessionWakes.Due("ABC-42", "wake", new Standing(TaskStatus.IN_PROGRESS, "NONE", 0, 0)));
        assertThat(second).isEmpty();
    }

    @Test
    void keepsADueWakePendingWhileTheHumanIsTypingAndSendsItOnceAfter(@TempDir Path dir) {
        Path file = dir.resolve("state.json");
        new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(dir.toString()).withStateFile(file.toString())))
                .putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");
        SessionWakes later = sessionWakes(file, RESET);

        List<SessionWakes.Due> whileTyping = later.takeDue(taskId -> true);
        List<SessionWakes.Due> afterSending = later.takeDue(taskId -> false);
        List<SessionWakes.Due> again = later.takeDue(taskId -> false);

        assertThat(whileTyping).isEmpty();
        assertThat(afterSending).containsExactly(new SessionWakes.Due("ABC-42", "wake", new Standing(TaskStatus.IN_PROGRESS, "NONE", 0, 0)));
        assertThat(again).isEmpty();
    }

    @Test
    void doesNotWakeASessionTwiceAcrossARestart(@TempDir Path dir) {
        Path file = dir.resolve("state.json");
        new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(dir.toString()).withStateFile(file.toString())))
                .putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");
        sessionWakes(file, RESET).takeDue(taskId -> false);

        List<SessionWakes.Due> afterRestart = sessionWakes(file, RESET.plusSeconds(60)).takeDue(taskId -> false);

        assertThat(afterRestart).isEmpty();
    }

    @Test
    void keepsAPendingWakeAcrossARestart(@TempDir Path dir) {
        Path file = dir.resolve("state.json");
        new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(dir.toString()).withStateFile(file.toString())))
                .putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");

        List<SessionWakes.Due> afterRestart = sessionWakes(file, RESET).takeDue(taskId -> false);

        assertThat(afterRestart).containsExactly(new SessionWakes.Due("ABC-42", "wake", new Standing(TaskStatus.IN_PROGRESS, "NONE", 0, 0)));
    }

    @Test
    void armsNoWakeWhenNoWindowIsAtItsLimit(@TempDir Path dir) {
        Path file = dir.resolve("state.json");
        new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(dir.toString()).withStateFile(file.toString())))
                .putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        wakes(reading(99, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");

        List<SessionWakes.Due> due = sessionWakes(file, RESET).takeDue(taskId -> false);

        assertThat(due).isEmpty();
    }

    @Test
    void armsNoWakeWhenTheFullWindowReportsNoReset(@TempDir Path dir) {
        Path file = dir.resolve("state.json");
        new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(dir.toString()).withStateFile(file.toString())))
                .putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        wakes(reading(100, Optional.empty()), true, file, STOPPED).arm("ABC-42");

        List<SessionWakes.Due> due = sessionWakes(file, RESET).takeDue(taskId -> false);

        assertThat(due).isEmpty();
    }

    @Test
    void armsNoWakeWhileTheKeyIsOff(@TempDir Path dir) {
        Path file = dir.resolve("state.json");
        new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(dir.toString()).withStateFile(file.toString())))
                .putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        wakes(reading(100, Optional.of(RESET)), false, file, STOPPED).arm("ABC-42");

        List<SessionWakes.Due> due = sessionWakes(file, RESET).takeDue(taskId -> false);

        assertThat(due).isEmpty();
    }

    @Test
    void dropsTheWakeWhenTheSessionMovesAgain(@TempDir Path dir) {
        Path file = dir.resolve("state.json");
        new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(dir.toString()).withStateFile(file.toString())))
                .putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");
        SessionWakes later = sessionWakes(file, RESET);

        later.cancel("ABC-42");

        assertThat(later.takeDue(taskId -> false)).isEmpty();
    }

    @Test
    void wakesAtTheLaterResetWhenTwoWindowsAreFull(@TempDir Path dir) {
        Path file = dir.resolve("state.json");
        new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(dir.toString()).withStateFile(file.toString())))
                .putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        SubscriptionUsage usage = () -> new SubscriptionUsage.Read(new SubscriptionUsage.Snapshot(List.of(
                new SubscriptionUsage.Window("5h", 100, Optional.of(RESET)),
                new SubscriptionUsage.Window("week", 100, Optional.of(RESET.plusSeconds(86400)))), Optional.empty()));
        wakes(new UsageLimits(Optional.of(usage)), true, file, STOPPED).arm("ABC-42");

        List<SessionWakes.Due> atFirstReset = sessionWakes(file, RESET).takeDue(taskId -> false);

        assertThat(atFirstReset).isEmpty();
    }

    @Test
    void dropsAWakeOnceAnotherTurnOpenedAfterItWasArmed(@TempDir Path dir) {
        Path file = dir.resolve("state.json");
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(dir.toString()).withStateFile(file.toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");
        new TaskTurns(state, at(STOPPED)).opened("ABC-42", "context", "a1b2", TaskStatus.IN_PROGRESS);

        List<SessionWakes.Due> due = sessionWakes(file, RESET).takeDue(taskId -> false);

        assertThat(due).isEmpty();
    }

    @Test
    void wakesWithTheStepItWasArmedFor(@TempDir Path dir) {
        Path file = dir.resolve("state.json");
        new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(dir.toString()).withStateFile(file.toString())))
                .putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        sessionWakes(file, STOPPED).arm("ABC-42", RESET, "wait-over");

        List<SessionWakes.Due> due = sessionWakes(file, RESET).takeDue(taskId -> false);

        assertThat(due).containsExactly(new SessionWakes.Due("ABC-42", "wait-over", new Standing(TaskStatus.IN_PROGRESS, "NONE", 0, 0)));
    }
}
