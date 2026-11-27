package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskReducerUnclassifiedTest {

  @Test
  void refusesClaimWhereChartHasNoClassForItsKindAndSaysSo() {
    List<Unclassified> found = new ArrayList<>();
    TaskChart chart = mock(TaskChart.class);
    when(chart.unclassified(any(), any())).thenReturn(List.of("lifecycle.NEW"));
    TaskReducer reducer = new TaskReducer(chart, stale -> {}, found::add);
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.NEW).build();

    assertThatThrownBy(() ->
            reducer.reduce(
                task, new TaskMove.Reported(TaskStatus.IN_PROGRESS, false), null, false, 10L))
        .isInstanceOf(Refusal.class);
    assertThat(found).containsExactly(new Unclassified("Reported", List.of("lifecycle.NEW"), true));
  }

  @Test
  void takesFactWhereChartHasNoClassForItsKindAndSaysSo() {
    List<Unclassified> found = new ArrayList<>();
    TaskChart chart = mock(TaskChart.class);
    when(chart.unclassified(any(), any())).thenReturn(List.of("request.OPEN"));
    when(chart.effects(any(), any())).thenReturn(List.of());
    TaskReducer reducer = new TaskReducer(chart, stale -> {}, found::add);
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    Generation readAt = new Generation(Generation.Kind.HEAD, "");

    TaskState after =
        reducer.reduce(task, new TaskMove.MergedByHost(readAt, "api", false), null, false, 10L);

    assertThat(after.standing().status()).isEqualTo(TaskStatus.IN_PROGRESS);
    assertThat(found)
        .containsExactly(new Unclassified("MergedByHost", List.of("request.OPEN"), false));
  }
}
