package dev.jagt.orchestrator.flow;

import static dev.jagt.orchestrator.flow.TestPressEngineBuilder.press;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowEnginePressTest {

  private final ReducingStore tasks = mock(ReducingStore.class);
  private final AgentPresence agents = mock(AgentPresence.class);
  private final List<String> ran = new ArrayList<>();

  private final TaskState inProgress =
      TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

  private final TaskState stopped =
      TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
          .stop(new TaskStop(TaskStop.Trigger.ROUNDS, 2, "three rounds"))
          .build();

  @BeforeEach
  void taskIsKnownByItsId() {
    when(tasks.canonicalTaskId("ABC-42")).thenReturn("ABC-42");
  }

  @Test
  void refusesRevertNamingWhatGitNowObeys() {
    when(tasks.task("ABC-42"))
        .thenReturn(Optional.of(TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()));
    FlowEngine engine =
        press(ran)
            .withAction(TaskAction.REVERT)
            .withTrust(id -> Optional.of("core.fsmonitor in /r/.git/config added"))
            .build(tasks, agents);

    assertThatThrownBy(() -> engine.run("ABC-42", TaskAction.REVERT))
        .isInstanceOf(Refusal.class)
        .hasMessage("revert ABC-42 refused: core.fsmonitor in /r/.git/config added");
    assertThat(ran).isEmpty();
  }

  @Test
  void runsTheVerbWhileGitObeysWhatWasTrusted() {
    when(tasks.task("ABC-42")).thenReturn(Optional.of(inProgress));
    FlowEngine engine = press(ran).build(tasks, agents);

    String shown = engine.run("ABC-42", TaskAction.SHIP);

    assertThat(shown).isEqualTo("ship ABC-42: done");
  }

  @Test
  void refusesWithTheUnreadableSentenceWhereGitStateCannotBeRead() {
    when(tasks.task("ABC-42")).thenReturn(Optional.of(inProgress));
    FlowEngine engine =
        press(ran)
            .withTrust(id -> {
              throw Refusal.byState("git state of " + id + " could not be read: boom");
            })
            .build(tasks, agents);

    assertThatThrownBy(() -> engine.run("ABC-42", TaskAction.SHIP))
        .isInstanceOf(Refusal.class)
        .hasMessage("git state of ABC-42 could not be read: boom");
    assertThat(ran).isEmpty();
  }

  @Test
  void readsNothingFromGitForVerbItDoesNotGate() {
    when(tasks.task("ABC-42")).thenReturn(Optional.of(inProgress));
    List<String> reads = new ArrayList<>();
    FlowEngine engine =
        press(ran)
            .withAction(TaskAction.FOCUS)
            .withTrust(id -> {
              reads.add("git");
              return Optional.empty();
            })
            .withMerged(id -> {
              reads.add("merged");
              return false;
            })
            .build(tasks, agents);

    engine.run("ABC-42", TaskAction.FOCUS);

    assertThat(reads).isEmpty();
  }

  @Test
  void readsGitOnceWhereTheShipIsRefused() {
    when(tasks.task("ABC-42")).thenReturn(Optional.of(inProgress));
    List<String> reads = new ArrayList<>();
    FlowEngine engine =
        press(ran)
            .withTrust(id -> {
              reads.add("git");
              return Optional.of("core.fsmonitor in /r/.git/config added");
            })
            .build(tasks, agents);

    assertThatThrownBy(() -> engine.run("ABC-42", TaskAction.SHIP)).isInstanceOf(Refusal.class);
    assertThat(reads).hasSize(1);
  }

  @Test
  void refusesTheMastersShipOnStoppedTask() {
    when(tasks.task("ABC-42")).thenReturn(Optional.of(stopped));
    FlowEngine engine = press(ran).withOrigin(() -> ActionOrigin.MASTER).build(tasks, agents);

    assertThatThrownBy(() -> engine.run("ABC-42", TaskAction.SHIP))
        .isInstanceOf(Refusal.class)
        .hasMessage("ship ABC-42 refused: jagt stopped it; only a human presses it until `lift`");
    assertThat(ran).isEmpty();
  }

  @Test
  void runsTheHumansOwnPressOnStoppedTask() {
    when(tasks.task("ABC-42")).thenReturn(Optional.of(stopped));
    FlowEngine engine = press(ran).build(tasks, agents);

    engine.run("ABC-42", TaskAction.SHIP);

    assertThat(ran).containsExactly("ship");
  }

  @Test
  void shipsNothingWhereGitFindsEveryRequestMerged() {
    when(tasks.task("ABC-42")).thenReturn(Optional.of(inProgress));
    FlowEngine engine = press(ran).withMerged(id -> true).build(tasks, agents);

    String shown = engine.run("ABC-42", TaskAction.SHIP);

    assertThat(shown).isEqualTo("ship ABC-42: every request is already merged — nothing to ship");
    assertThat(ran).isEmpty();
  }
}
