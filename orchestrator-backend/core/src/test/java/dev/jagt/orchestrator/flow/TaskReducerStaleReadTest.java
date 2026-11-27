package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskReducerStaleReadTest {

  @Test
  void dropsSignReadBeforeTheSessionStartedOverWithItsReasonNamed() {
    List<Generation.Stale> dropped = new ArrayList<>();
    TaskReducer reducer =
        new TaskReducer(new TaskChart(Composition.builtIn()), dropped::add, gap -> {});
    TaskState task =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).alias("ABC-42").build();
    Generation readAt = new Generation(Generation.Kind.SESSION_START, "0");
    TaskState restarted = reducer.sessionStartedOver(task);

    TaskState landed = reducer.signed(restarted, readAt, new SessionSign.WorkStarted(), 2_000);

    assertThat(landed).isEqualTo(restarted);
    assertThat(dropped)
        .containsExactly(new Generation.Stale("ABC-42", Generation.Kind.SESSION_START, "0", "1"));
  }

  @Test
  void landsSignReadOnTheSessionStillRunning() {
    List<Generation.Stale> dropped = new ArrayList<>();
    TaskReducer reducer =
        new TaskReducer(new TaskChart(Composition.builtIn()), dropped::add, gap -> {});
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    Generation readAt = new Generation(Generation.Kind.SESSION_START, "0");

    TaskState landed = reducer.signed(task, readAt, new SessionSign.WorkStarted(), 2_000);

    assertThat(landed.work().session().workStartedAt()).isEqualTo(2_000);
    assertThat(dropped).isEmpty();
  }

  @Test
  void headGenerationIsTheNewestTipSeen() {
    TaskReducer reducer =
        new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {}, gap -> {});
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.CI_POLLING).build();

    Generation head = Generation.of(Generation.Kind.HEAD, reducer.headSeen(task, "abc"));

    assertThat(head).isEqualTo(new Generation(Generation.Kind.HEAD, "abc"));
  }

  @Test
  void roundGenerationIsTheRequestsLinked() {
    TaskReducer reducer =
        new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {}, gap -> {});
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState linked =
        reducer.requestLinked(task, Map.of(), "https://host.example/1", false, 1_000);

    Generation round = Generation.of(Generation.Kind.ROUND, linked);

    assertThat(round).isEqualTo(new Generation(Generation.Kind.ROUND, "https://host.example/1"));
  }
}
