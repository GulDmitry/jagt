package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.RoundTrail;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TaskStopsTest {

    private final Notifications notifications = mock(Notifications.class);
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(100_000_000), ZoneOffset.UTC);

    @Test
    void stopsATaskWhoseRoundsResolveNothingAndTellsTheHumanOnce(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)), clock);
        ReviewFacts round = new ReviewFacts(true, false, "failed", "unit-tests\nCartTest failed",
                List.of("https://host/mr/1#note_1", "https://host/mr/1#note_2"), 0, List.of(), List.of("note_1", "note_2"));
        stops.round("ABC-42", round, "brief 1");
        stops.round("ABC-42", round, "brief 2");
        stops.round("ABC-42", round, "brief 3");

        TaskStops.Relay fourth = stops.round("ABC-42", round, "brief 4");
        TaskStops.Relay fifth = stops.round("ABC-42", round, "brief 5");

        assertThat(fourth).isEqualTo(TaskStops.Relay.STOPPED);
        assertThat(fifth).isEqualTo(TaskStops.Relay.STOPPED);
        assertThat(state.task("ABC-42").orElseThrow().stop())
                .extracting(TaskStop::trigger).isEqualTo(TaskStop.Trigger.STALLED);
        verify(notifications, times(1)).send(any());
    }

    @Test
    void refusesTheFifthUnaskedShipWithinTheWindow(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 99_000_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 99_200_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 99_400_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 99_600_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVIEW_PENDING, 99_800_000, ActionOrigin.MCP))).build());

        boolean may = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)), clock).mayShip("ABC-42");

        assertThat(may).isFalse();
        assertThat(state.task("ABC-42").orElseThrow().stop())
                .extracting(TaskStop::trigger).isEqualTo(TaskStop.Trigger.SHIPS);
    }

    @Test
    void relaysTheStoppedRoundAgainOnceTheHumanLiftsTheStop(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .trail(RoundTrail.none().withRound(new RoundTrail.Round(1, 2, "", "same", "")))
                .stop(new TaskStop(TaskStop.Trigger.STALLED, 2, "3 rounds")).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)), clock);

        stops.lift("ABC-42");

        assertThat(stops.round("ABC-42", new ReviewFacts(true, false, "running", "", List.of("x"), 0, List.of(), List.of()), "same"))
                .isEqualTo(TaskStops.Relay.RELAY);
    }

    @Test
    void countsAShipsStopOnTheRequestOpenAndAVerificationStopOnNone(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build()
                .withMrUrl("https://host/mr/571", 1));
        state.putTask("ABC-43", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build()
                .withMrUrl("https://host/mr/572", 1));
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)), clock);

        stops.stop("ABC-42", TaskStop.Trigger.SHIPS, "4 ships");
        stops.stop("ABC-43", TaskStop.Trigger.ROUNDS, "3 rounds");

        assertThat(state.task("ABC-42").orElseThrow().stop().request()).isEqualTo("https://host/mr/571");
        assertThat(state.task("ABC-43").orElseThrow().stop().request()).isNull();
    }

    @Test
    void letsAFreshTaskShip(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)), clock);

        assertThat(stops.mayShip("ABC-42")).isTrue();
        assertThat(state.task("ABC-42").orElseThrow().stopped()).isFalse();
    }

    @Test
    void reportsATaskStoppedOnlyOnceAStopStandsOnIt(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .stop(new TaskStop(TaskStop.Trigger.STALLED, 2, "3 rounds")).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)), clock);

        assertThat(stops.stopped("ABC-1")).isFalse();
        assertThat(stops.stopped("ABC-2")).isTrue();
        assertThat(stops.stopped("ABC-9")).isFalse();
    }

    @Test
    void answersATripWithTheStopItWroteAndLaterWithTheOneStanding(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)), clock);
        TaskStop first = new TaskStop(TaskStop.Trigger.CHECKS, 5, "3 reads could not list the checks");

        Optional<TaskStop> tripped = stops.trip("ABC-42", task -> Optional.of(first));
        Optional<TaskStop> standing = stops.trip("ABC-42", task -> Optional.empty());

        assertThat(tripped).contains(first);
        assertThat(standing).contains(first);
        assertThat(state.task("ABC-42").orElseThrow().stop()).isEqualTo(first);
    }

    @Test
    void answersARoundTheLastRelayedOneRepeatsAsUnchanged(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)), clock);
        ReviewFacts round = new ReviewFacts(true, false, "running", "", List.of("x"), 0, List.of(), List.of());

        TaskStops.Relay first = stops.round("ABC-42", round, "brief");
        TaskStops.Relay again = stops.round("ABC-42", round, "brief");

        assertThat(first).isEqualTo(TaskStops.Relay.RELAY);
        assertThat(again).isEqualTo(TaskStops.Relay.UNCHANGED);
        assertThat(state.task("ABC-42").orElseThrow().trailOrNone().rounds()).hasSize(1);
    }

    @Test
    void recordsTheFirstLineOfTheFailureOnlyForARedRound(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)), clock);

        stops.round("ABC-42", new ReviewFacts(true, false, "failed", "unit-tests\nCartTest failed", List.of("x"), 0,
                List.of(), List.of()), "red");
        stops.round("ABC-42", new ReviewFacts(true, false, "running", "unit-tests\nCartTest failed", List.of("x"), 0,
                List.of(), List.of()), "running");

        assertThat(state.task("ABC-42").orElseThrow().trailOrNone().rounds()).extracting(RoundTrail.Round::failing)
                .containsExactly("unit-tests", "");
    }

    @Test
    void answersAReadOfTheSameThreadsAndNewestNotesAsUnchangedWhateverTheCopiedText(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)), clock);
        ReviewFacts first = new ReviewFacts(true, false, "success", "",
                List.of("https://host/mr/1#note_1\nmia: Why?\nbot: Fair enough."), 0, List.of(), List.of("note_7"));
        ReviewFacts second = new ReviewFacts(true, false, "success", "",
                List.of("https://host/mr/1#note_1\nmia: Why?\nbot: Fair enough, conceded."), 0, List.of(), List.of("note_7"));

        stops.round("ABC-42", first, "brief with one copy");
        TaskStops.Relay again = stops.round("ABC-42", second, "brief with another copy");

        assertThat(again).isEqualTo(TaskStops.Relay.UNCHANGED);
        assertThat(state.task("ABC-42").orElseThrow().trailOrNone().rounds()).hasSize(1);
    }

    @Test
    void relaysARoundWhereANewNoteCameOnTheSameThread(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)), clock);
        ReviewFacts first = new ReviewFacts(true, false, "success", "",
                List.of("https://host/mr/1#note_1\nmia: Why?"), 0, List.of(), List.of("note_7"));
        ReviewFacts second = new ReviewFacts(true, false, "success", "",
                List.of("https://host/mr/1#note_1\nmia: Why?\nmia: And this?"), 0, List.of(), List.of("note_8"));

        stops.round("ABC-42", first, "brief one");
        TaskStops.Relay next = stops.round("ABC-42", second, "brief two");

        assertThat(next).isEqualTo(TaskStops.Relay.RELAY);
    }

    @Test
    void journalsAStopOnceWhenItTrips(@TempDir Path root) throws IOException {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("cart").build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)), clock);

        stops.stop("ABC-42", TaskStop.Trigger.CHECKS, "3 reads could not list the checks");
        stops.stop("ABC-42", TaskStop.Trigger.ROUNDS, "later");

        assertThat(Files.readAllLines(root.resolve("stops.jsonl"))).containsExactly(
                "{\"at\":100000000,\"task\":\"ABC-42\",\"alias\":\"cart\",\"trigger\":\"CHECKS\","
                        + "\"status\":\"CI_POLLING\",\"evidence\":\"3 reads could not list the checks\"}");
    }

    @Test
    void journalsNothingWhenALiftClearsTheStop(@TempDir Path root) throws IOException {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)), clock);
        stops.stop("ABC-42", TaskStop.Trigger.CHECKS, "3 reads could not list the checks");

        stops.lift("ABC-42");

        assertThat(Files.readAllLines(root.resolve("stops.jsonl"))).hasSize(1);
        assertThat(stops.stopped("ABC-42")).isFalse();
    }
}
