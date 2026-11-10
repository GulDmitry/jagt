package dev.jagt.orchestrator.surface.board;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.TaskView;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommandAssistant;
import dev.jagt.orchestrator.port.CommandAssistant.CommandProposal;
import dev.jagt.orchestrator.service.AutoReviewCadence;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.service.Rounds;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.json.JsonMapper;

final class BoardFixtures {

  private BoardFixtures() {}

  static StateService emptyState(Path root) {
    return new StateService(
        new JsonMapper(),
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString())),
        new TaskReducer(new TaskChart(Composition.builtIn())),
        event -> {});
  }

  static TaskState widgetTask() {
    return TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
        .alias("a1")
        .title("Widget layout is off")
        .build();
  }

  static PaletteContext paletteOver(StateService state) {
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    return new PaletteContext(
        state,
        new TaskViews(
            state,
            config,
            new Rounds(
                config,
                ignored -> false,
                () -> false,
                new Checkers(new TaskChart(Composition.builtIn()))),
            Clock.systemUTC(),
            new FlowRules(new TaskChart(Composition.builtIn()))));
  }

  static CommandAssistant proposing(String verb, String task, String argument, String why) {
    return (text, tasks) ->
        new Answer<>(
            Optional.of(new CommandProposal(verb, task, argument, why)), TokenUsage.NONE);
  }

  static NaturalLanguageDispatch dispatching(
      CommandAssistant assistant,
      StateService state,
      CommandService commands,
      TaskLauncher launcher) {
    return new NaturalLanguageDispatch(assistant, widgetPalette(state), commands, launcher);
  }

  static PaletteContext widgetPalette(StateService state) {
    state.putTask("ABC-1", widgetTask());
    return paletteOver(state);
  }

  static TaskViews.Snapshot snapshotOf(
      List<TaskView> tasks, boolean autoReview, List<String> projects) {
    return new TaskViews.Snapshot(
        tasks,
        new AutoReviewCadence(autoReview, Duration.ofHours(24), 10, 60, 240),
        projects,
        null,
        "off");
  }

  static HeaderState quietHeader() {
    return headerOf(List.of(), new ReachLedger());
  }

  static HeaderState headerOf(List<Job> jobs, ReachLedger reach) {
    return new HeaderState(
        new Jobs(jobs, mock(TaskScheduler.class), Clock.systemUTC(), mock(StateService.class)),
        reach,
        Clock.systemUTC());
  }
}
