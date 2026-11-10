package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.ModelTier;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.RoundTrail;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.StopTriage;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

class TaskStopsTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final Notifications notifications = mock(Notifications.class);
  private final Clock clock = Clock.fixed(Instant.ofEpochMilli(100_000_000), ZoneOffset.UTC);

  @Test
  void stopsTaskWhoseRoundsResolveNothingAndTellsTheHumanOnce(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    ReviewFacts round =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("failed", "unit-tests\nCartTest failed", ""),
            new ReviewFacts.Discussion(
                List.of("https://host/mr/1#note_1", "https://host/mr/1#note_2"),
                List.of(),
                List.of("note_1", "note_2")));
    stops.round("ABC-42", round, "brief 1", "");
    stops.round("ABC-42", round, "brief 2", "");
    stops.round("ABC-42", round, "brief 3", "");

    TaskStops.Relay fourth = stops.round("ABC-42", round, "brief 4", "");
    TaskStops.Relay fifth = stops.round("ABC-42", round, "brief 5", "");

    assertThat(fourth).isEqualTo(TaskStops.Relay.STOPPED);
    assertThat(fifth).isEqualTo(TaskStops.Relay.STOPPED);
    assertThat(state.task("ABC-42").orElseThrow().stop())
        .extracting(TaskStop::trigger)
        .isEqualTo(TaskStop.Trigger.STALLED);
    verify(notifications, times(1)).send(any());
  }

  @Test
  void keepsStopsEvidenceUpToTheCapAndFlagsTheCut(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    TaskStop stop =
        stops.stop("ABC-42", TaskStop.Trigger.ACCEPTANCE_FAILED, "x".repeat(5_000)).orElseThrow();

    assertThat(stop.evidence()).isEqualTo("x".repeat(4_000) + "\n[cut]");
  }

  @Test
  void keepsEvidenceWholeWhenItIsExactlyTheCap(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    TaskStop stop =
        stops.stop("ABC-42", TaskStop.Trigger.ACCEPTANCE_FAILED, "x".repeat(4_000)).orElseThrow();

    assertThat(stop.evidence()).isEqualTo("x".repeat(4_000));
  }

  @Test
  void refusesTheFifthUnaskedShipWithinTheWindow(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .history(
                List.of(
                    new StatusChange(TaskStatus.SHIPPING, 99_000_000, ActionOrigin.MASTER),
                    new StatusChange(TaskStatus.SHIPPING, 99_200_000, ActionOrigin.MASTER),
                    new StatusChange(TaskStatus.SHIPPING, 99_400_000, ActionOrigin.MASTER),
                    new StatusChange(TaskStatus.SHIPPING, 99_600_000, ActionOrigin.MASTER),
                    new StatusChange(TaskStatus.REVIEW_PENDING, 99_800_000, ActionOrigin.MCP)))
            .build());

    boolean may =
        new TaskStops(
                new StopPolicy(
                    ignored -> Arbiter.Profile.OFF,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
                state,
                clock,
                new FlowRules(new TaskChart(Composition.builtIn())))
            .mayShip("ABC-42");

    assertThat(may).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().stop())
        .extracting(TaskStop::trigger)
        .isEqualTo(TaskStop.Trigger.SHIPS);
  }

  @Test
  void relaysTheStoppedRoundAgainOnceTheHumanLiftsTheStop(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .trail(RoundTrail.none().withRound(new RoundTrail.Round(1, 2, "", "same", "")))
            .stop(new TaskStop(TaskStop.Trigger.STALLED, 2, "3 rounds"))
            .build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    stops.lift("ABC-42");

    assertThat(
            stops.round(
                "ABC-42",
                new ReviewFacts(
                    new ReviewFacts.Request(true, false, false, false, 0),
                    new ReviewFacts.Pipeline("running", "", ""),
                    new ReviewFacts.Discussion(List.of("x"), List.of(), List.of())),
                "same",
                ""))
        .isEqualTo(TaskStops.Relay.RELAY);
  }

  @Test
  void countsShipsStopOnTheRequestOpenAndVerificationStopOnNone(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withMrUrl("https://host/mr/571", 1));
    state.putTask(
        "ABC-43",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .build()
            .withMrUrl("https://host/mr/572", 1));
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    stops.stop("ABC-42", TaskStop.Trigger.SHIPS, "4 ships");
    stops.stop("ABC-43", TaskStop.Trigger.ROUNDS, "3 rounds");

    assertThat(Objects.requireNonNull(state.task("ABC-42").orElseThrow().stop()).request())
        .isEqualTo("https://host/mr/571");
    assertThat(Objects.requireNonNull(state.task("ABC-43").orElseThrow().stop()).request())
        .isNull();
  }

  @Test
  void letsFreshTaskShip(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    assertThat(stops.mayShip("ABC-42")).isTrue();
    assertThat(state.task("ABC-42").orElseThrow().stopped()).isFalse();
  }

  @Test
  void reportsTaskStoppedOnlyOnceStopStandsOnIt(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    state.putTask(
        "ABC-2",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .stop(new TaskStop(TaskStop.Trigger.STALLED, 2, "3 rounds"))
            .build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    assertThat(stops.stopped("ABC-1")).isFalse();
    assertThat(stops.stopped("ABC-2")).isTrue();
    assertThat(stops.stopped("ABC-9")).isFalse();
  }

  @Test
  void answersTripWithTheStopItWroteAndLaterWithTheOneStanding(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    TaskStop first = new TaskStop(TaskStop.Trigger.CHECKS, 5, "3 reads could not list the checks");

    Optional<TaskStop> tripped = stops.trip("ABC-42", task -> Optional.of(first));
    Optional<TaskStop> standing = stops.trip("ABC-42", task -> Optional.empty());

    assertThat(tripped).contains(first);
    assertThat(standing).contains(first);
    assertThat(state.task("ABC-42").orElseThrow().stop()).isEqualTo(first);
  }

  @Test
  void answersRoundTheLastRelayedOneRepeatsAsUnchanged(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    ReviewFacts round =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("running", "", ""),
            new ReviewFacts.Discussion(List.of("x"), List.of(), List.of()));

    TaskStops.Relay first = stops.round("ABC-42", round, "brief", "");
    TaskStops.Relay again = stops.round("ABC-42", round, "brief", "");

    assertThat(first).isEqualTo(TaskStops.Relay.RELAY);
    assertThat(again).isEqualTo(TaskStops.Relay.UNCHANGED);
    assertThat(state.task("ABC-42").orElseThrow().trailOrNone().rounds()).hasSize(1);
  }

  @Test
  void recordsTheFirstLineOfTheFailureOnlyForRedRound(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    stops.round(
        "ABC-42",
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("failed", "unit-tests\nCartTest failed", ""),
            new ReviewFacts.Discussion(List.of("x"), List.of(), List.of())),
        "red",
        "");
    stops.round(
        "ABC-42",
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("running", "unit-tests\nCartTest failed", ""),
            new ReviewFacts.Discussion(List.of("x"), List.of(), List.of())),
        "running",
        "");

    assertThat(state.task("ABC-42").orElseThrow().trailOrNone().rounds())
        .extracting(RoundTrail.Round::failing)
        .containsExactly("unit-tests", "");
  }

  @Test
  void answersReadOfTheSameThreadsAndNewestNotesAsUnchangedWhateverTheCopiedText(
      @TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    ReviewFacts first =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(
                List.of("https://host/mr/1#note_1\nmia: Why?\nbot: Fair enough."),
                List.of(),
                List.of("note_7")));
    ReviewFacts second =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(
                List.of("https://host/mr/1#note_1\nmia: Why?\nbot: Fair enough, conceded."),
                List.of(),
                List.of("note_7")));

    stops.round("ABC-42", first, "brief with one copy", "");
    TaskStops.Relay again = stops.round("ABC-42", second, "brief with another copy", "");

    assertThat(again).isEqualTo(TaskStops.Relay.UNCHANGED);
    assertThat(state.task("ABC-42").orElseThrow().trailOrNone().rounds()).hasSize(1);
  }

  @Test
  void relaysRoundWhereNewNoteCameOnTheSameThread(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    ReviewFacts first =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(
                List.of("https://host/mr/1#note_1\nmia: Why?"), List.of(), List.of("note_7")));
    ReviewFacts second =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(
                List.of("https://host/mr/1#note_1\nmia: Why?\nmia: And this?"),
                List.of(),
                List.of("note_8")));

    stops.round("ABC-42", first, "brief one", "");
    TaskStops.Relay next = stops.round("ABC-42", second, "brief two", "");

    assertThat(next).isEqualTo(TaskStops.Relay.RELAY);
  }

  @Test
  void answersTheSameFailingJobOnTheSameCommitAsUnchangedWhateverTheBriefsWording(
      @TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    ReviewFacts first =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("failed", "unit-tests\nCartTest failed: expected 60", ""),
            new ReviewFacts.Discussion(List.of(), List.of(), List.of()));
    ReviewFacts second =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("failed", "unit-tests\nCartTest failed: expected 60 but", ""),
            new ReviewFacts.Discussion(List.of(), List.of(), List.of()));

    stops.round("ABC-42", first, "brief worded one way", "api=a1b2c3");
    TaskStops.Relay again = stops.round("ABC-42", second, "brief worded another way", "api=a1b2c3");

    assertThat(again).isEqualTo(TaskStops.Relay.UNCHANGED);
  }

  @Test
  void relaysTheSameFailingJobOnceNewCommitWasPushed(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    ReviewFacts round =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("failed", "unit-tests\nCartTest failed", ""),
            new ReviewFacts.Discussion(List.of(), List.of(), List.of()));

    stops.round("ABC-42", round, "same brief", "api=a1b2c3");
    TaskStops.Relay next = stops.round("ABC-42", round, "same brief", "api=d4e5f6");

    assertThat(next).isEqualTo(TaskStops.Relay.RELAY);
  }

  @Test
  void journalsStopOnceWhenItTrips(@TempDir Path root) throws IOException {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("cart").build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    stops.stop("ABC-42", TaskStop.Trigger.CHECKS, "3 reads could not list the checks");
    stops.stop("ABC-42", TaskStop.Trigger.ROUNDS, "later");

    assertThat(Files.readAllLines(root.resolve("stops.jsonl")))
        .containsExactly(
            "{\"at\":100000000,\"task\":\"ABC-42\",\"alias\":\"cart\",\"trigger\":\"CHECKS\","
                + "\"status\":\"CI_POLLING\",\"evidence\":\"3 reads could not list the checks\"}");
  }

  @Test
  void journalsNothingWhenLiftClearsTheStop(@TempDir Path root) throws IOException {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    stops.stop("ABC-42", TaskStop.Trigger.CHECKS, "3 reads could not list the checks");

    stops.lift("ABC-42");

    assertThat(Files.readAllLines(root.resolve("stops.jsonl"))).hasSize(1);
    assertThat(stops.stopped("ABC-42")).isFalse();
  }

  @Test
  void relaysThreadWithNewNoteWhileTheAgentsQuestionAboutTheChecksStands(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .report(AgentReport.QUESTION)
            .trail(RoundTrail.none().withRound(new RoundTrail.Round(1, 1, "", "a", "t1 note_7")))
            .build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    ReviewFacts answered =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(
                List.of("t1\nmia: And this?"), List.of(), List.of("note_8")));

    TaskStops.Relay relay = stops.round("ABC-42", answered, "brief", "");

    assertThat(relay).isEqualTo(TaskStops.Relay.RELAY);
  }

  @ParameterizedTest
  @CsvSource({"INFRA,GREEN", "INFRA,RUNNING", "INFRA,NONE", "CHECKS,GREEN", "CHECKS,RUNNING"})
  void endsStopAnOutsideConditionSetOnReadThatListsTheChecksNotRed(
      TaskStop.Trigger trigger, Pipeline read, @TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    stops.stop("ABC-42", trigger, "host down");

    Optional<TaskStop> ended = stops.endOnRead("ABC-42", read);

    assertThat(ended).map(TaskStop::trigger).contains(trigger);
    assertThat(stops.stopped("ABC-42")).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "INFRA,RED",
    "INFRA,UNKNOWN",
    "CHECKS,RED",
    "CHECKS,UNKNOWN",
    "PACE,GREEN",
    "UNCHANGED_FAILURE,GREEN",
    "WAIT,RUNNING"
  })
  void keepsStopWhereTheReadIsRedOrUnreadOrTheStopIsForJudgement(
      TaskStop.Trigger trigger, Pipeline read, @TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    stops.stop("ABC-42", trigger, "host down");

    Optional<TaskStop> ended = stops.endOnRead("ABC-42", read);

    assertThat(ended).isEmpty();
    assertThat(stops.stopped("ABC-42")).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "INFRA,GONE,false",
    "CHECKS,GONE,false",
    "INFRA,UNREADABLE,true",
    "CHECKS,NO_MR,false",
    "INFRA,NO_MR,false",
    "PACE,GONE,true"
  })
  void endsStopOnRefusedReadOnlyWhereTheRequestIsGone(
      TaskStop.Trigger trigger,
      ReviewSweepService.SweepResult.Kind refusal,
      boolean stands,
      @TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    stops.stop("ABC-42", trigger, "host down");

    stops.endOnRefusal("ABC-42", refusal, "");

    assertThat(stops.stopped("ABC-42")).isEqualTo(stands);
  }

  @Test
  void endsStopWhereTheRequestItWasCountedOnIsGone(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .stop(
                new TaskStop(TaskStop.Trigger.INFRA, 1, "host down")
                    .countedOn(List.of("http://mr/A")))
            .build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    stops.endOnRefusal("ABC-42", ReviewSweepService.SweepResult.Kind.GONE, "http://mr/A");

    assertThat(stops.stopped("ABC-42")).isFalse();
  }

  @Test
  void keepsStopCountedOnOneRequestWhereAnotherRequestOfTheTaskIsGone(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .stop(
                new TaskStop(TaskStop.Trigger.INFRA, 1, "host down")
                    .countedOn(List.of("http://mr/A")))
            .build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    stops.endOnRefusal("ABC-42", ReviewSweepService.SweepResult.Kind.GONE, "http://mr/B");

    assertThat(stops.stopped("ABC-42")).isTrue();
  }

  @Test
  void stopsTaskWhoseUnattendedMovesFillTheHourBeforeRoundIsDue(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .history(
                IntStream.range(0, 20)
                    .mapToObj(
                        i ->
                            new StatusChange(
                                TaskStatus.CI_POLLING, 99_000_000 + i, ActionOrigin.MASTER))
                    .toList())
            .build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    boolean due = stops.roundDue("ABC-42", "review");

    assertThat(due).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().stop())
        .extracting(TaskStop::trigger)
        .isEqualTo(TaskStop.Trigger.PACE);
  }

  @ParameterizedTest
  @MethodSource("endsOnTheConditionThatEndsTheTrigger")
  void returnsTheStopAnEndingConditionEnded(
      TaskStop.Trigger trigger,
      Function<TaskStops, Optional<TaskStop>> ending,
      @TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    stops.stop("ABC-42", trigger, "down");

    Optional<TaskStop> ended = ending.apply(stops);

    assertThat(ended).map(TaskStop::trigger).contains(trigger);
    assertThat(stops.stopped("ABC-42")).isFalse();
  }

  static Stream<Arguments> endsOnTheConditionThatEndsTheTrigger() {
    return Stream.of(
        Arguments.of(
            TaskStop.Trigger.DEPLOY_UNREAD,
            (Function<TaskStops, Optional<TaskStop>>) stops -> stops.endOnDeployRead("ABC-42")),
        Arguments.of(
            TaskStop.Trigger.UNDELIVERED,
            (Function<TaskStops, Optional<TaskStop>>) stops -> stops.endOnSessionAlive("ABC-42")),
        Arguments.of(
            TaskStop.Trigger.WAIT,
            (Function<TaskStops, Optional<TaskStop>>) stops -> stops.endOnReport("ABC-42")),
        Arguments.of(
            TaskStop.Trigger.INFRA,
            (Function<TaskStops, Optional<TaskStop>>)
                stops ->
                    stops.endOnRefusal("ABC-42", ReviewSweepService.SweepResult.Kind.GONE, "")));
  }

  @Test
  void answersWhetherStopOfTheTriggerStandsOnTheTask(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .stop(new TaskStop(TaskStop.Trigger.INFRA, 2, "down"))
            .build());
    state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    assertThat(stops.standing("ABC-1", TaskStop.Trigger.INFRA)).isTrue();
    assertThat(stops.standing("ABC-1", TaskStop.Trigger.CHECKS)).isFalse();
    assertThat(stops.standing("ABC-2", TaskStop.Trigger.INFRA)).isFalse();
    assertThat(stops.standing("ABC-9", TaskStop.Trigger.INFRA)).isFalse();
  }

  @Test
  void answersWhetherTheAgentsQuestionStandsOnTheTask(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .report(AgentReport.QUESTION)
            .build());
    state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    assertThat(stops.asking("ABC-1")).isTrue();
    assertThat(stops.asking("ABC-2")).isFalse();
    assertThat(stops.asking("ABC-9")).isFalse();
  }

  @Test
  void relaysRoundWithOpenThreadsAndNoNoteWhateverTheBriefsWording(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    ReviewFacts round =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("running", "", ""),
            new ReviewFacts.Discussion(List.of("x"), List.of(), List.of()));
    stops.round("ABC-42", round, "brief 1", "");

    TaskStops.Relay again = stops.round("ABC-42", round, "brief 2", "");

    assertThat(again).isEqualTo(TaskStops.Relay.RELAY);
  }

  @Test
  void relaysRoundWithNoThreadsAndGreenChecksWhateverTheBriefsWording(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    ReviewFacts round =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(List.of(), List.of(), List.of()));
    stops.round("ABC-42", round, "brief 1", "");

    TaskStops.Relay again = stops.round("ABC-42", round, "brief 2", "");

    assertThat(again).isEqualTo(TaskStops.Relay.RELAY);
  }

  @Test
  void tellsRelayedRoundsNotesFromTheLastRoundRelayed(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));
    stops.round(
        "ABC-42",
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(
                List.of("https://host/mr/1#note_1"), List.of(), List.of("note_1"))),
        "brief 1",
        "");
    ReviewFacts next =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("success", "", ""),
            new ReviewFacts.Discussion(
                List.of("https://host/mr/1#note_1"), List.of(), List.of("note_5")));

    String since = stops.sinceLast("ABC-42", next);

    assertThat(since).isEqualTo("Since the last round — new notes: 1.");
  }

  @Test
  void pausesStartedTaskOnTheHumansWordWithTheirReason(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    stops.pauseOnWord("ABC-42", "until the managers answer");

    assertThat(state.task("ABC-42").orElseThrow().stop())
        .extracting(TaskStop::trigger, TaskStop::evidence)
        .containsExactly(TaskStop.Trigger.PAUSE, "until the managers answer");
  }

  @Test
  void refusesPauseOnTheHumansWordWherePauseIsNotOffered(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    assertThatThrownBy(() -> stops.pauseOnWord("ABC-42", "until the managers answer"))
        .isInstanceOf(Refusal.class);
    assertThat(state.task("ABC-42").orElseThrow().stopped()).isFalse();
  }

  @Test
  void theMastersLiftSpendsItsBoundAndTheHumansLiftRestoresIt(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    TaskStop stop = new TaskStop(TaskStop.Trigger.ROUNDS, 5, "verify sent 3 rounds");
    state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).stop(stop).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    OriginContext.as(ActionOrigin.MASTER, () -> stops.lift("ABC-42"));
    int afterTheMasters = state.task("ABC-42").orElseThrow().masterOrNone().liftedStops();
    stops.stop("ABC-42", TaskStop.Trigger.SHIPS, "4 ships");
    OriginContext.as(ActionOrigin.BOARD, () -> stops.lift("ABC-42"));
    int afterTheHumans = state.task("ABC-42").orElseThrow().masterOrNone().liftedStops();

    assertThat(afterTheMasters).isEqualTo(1);
    assertThat(afterTheHumans).isZero();
  }

  @Test
  void humansEscalationClearsTheStopRaisesTheTierAndRestoresTheMastersLifts(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    TaskStop stop = new TaskStop(TaskStop.Trigger.ROUNDS, 5, "verify sent 3 rounds");
    state.putTask(
        "ABC-42",
        reducer.masterLiftedStops(
            TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .stop(stop)
                .build()
                .withTier(new ModelTier(List.of("sonnet", "opus"), 0)),
            1));
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    stops.escalate("ABC-42");

    TaskState escalated = state.task("ABC-42").orElseThrow();
    assertThat(escalated.stopped()).isFalse();
    assertThat(Objects.requireNonNull(escalated.tier()).current()).isEqualTo("opus");
    assertThat(escalated.masterOrNone().liftedStops()).isZero();
  }

  @Test
  void tellsTheHumanNothingOfStopTheMasterHoldsInAct(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                task -> new Arbiter.Profile(MasterMode.ACT, Set.of()),
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    stops.stop("ABC-42", TaskStop.Trigger.ROUNDS, "verify sent 3 rounds");

    verify(notifications, never()).send(any());
  }

  @Test
  void tellsTheHumanOfStopTheyKeepToLiftWhereTheMasterActs(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                task -> new Arbiter.Profile(MasterMode.ACT, Set.of("lift")),
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    stops.stop("ABC-42", TaskStop.Trigger.ROUNDS, "verify sent 3 rounds");

    verify(notifications, times(1)).send(any());
  }

  @Test
  void triageIsWrittenOnlyWhileTheStopItWasReadForStillStands(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.SHIPS, 5, "4 ships"))
            .build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    stops.triaged("ABC-42", "ROUNDS@5", StopTriage.failed("a stop that is gone"));

    assertThat(Objects.requireNonNull(state.task("ABC-42").orElseThrow().stop()).triage()).isNull();
  }

  @Test
  void spendsTheMastersLiftWhereTheMasterEscalatesStop(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.ROUNDS, 5, "verify sent 3 rounds"))
            .build()
            .withTier(new ModelTier(List.of("sonnet", "opus"), 0)));
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    OriginContext.as(ActionOrigin.MASTER, () -> stops.escalate("ABC-42"));

    assertThat(state.task("ABC-42").orElseThrow().masterOrNone().liftedStops()).isEqualTo(1);
  }

  @Test
  void keepsVerbFromTheMasterWhereTheProfileNamesItsId(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths, reducer, event -> {});
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    TaskStops stops =
        new TaskStops(
            new StopPolicy(
                task -> new Arbiter.Profile(MasterMode.ACT, Set.of("done")),
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn())));

    assertThat(stops.masterKeeps("ABC-42", TaskAction.DONE)).isTrue();
    assertThat(stops.masterKeeps("ABC-42", TaskAction.TRUST)).isFalse();
  }
}
