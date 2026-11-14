package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.LineChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.With;
import tools.jackson.databind.json.JsonMapper;

@With
public record TestPlanBaselineBuilder(Path root, Path worktree) {

  public static TestPlanBaselineBuilder planBaseline() {
    return new TestPlanBaselineBuilder(Path.of("/state"), Path.of("/wt"));
  }

  public PlanBaseline build() {
    OrchestratorPaths paths =
        TestOrchestratorPathsBuilder.orchestratorPaths().withRoot(root).build();
    StateService state =
        new StateService(
            new JsonMapper(),
            paths,
            new TaskReducer(new TaskChart(Composition.builtIn())),
            event -> {});
    state.putTask(
        "ABC-42", TaskState.builder("web", worktree.toString(), TaskStatus.PLAN_PENDING).build());
    return new PlanBaseline(
        paths,
        new TaskArtifacts(state),
        (earlier, later) -> new LineChange(Set.of(), Map.of(0, List.of(earlier))));
  }
}
