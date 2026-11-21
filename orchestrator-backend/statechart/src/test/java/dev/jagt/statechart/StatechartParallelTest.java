package dev.jagt.statechart;

import static dev.jagt.statechart.TestParallelBuilder.LEFT;
import static dev.jagt.statechart.TestParallelBuilder.OUTER;
import static dev.jagt.statechart.TestParallelBuilder.RIGHT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.statechart.TestIrpBuilder.Ev;
import dev.jagt.statechart.TestParallelBuilder.Branch;
import dev.jagt.statechart.TestParallelBuilder.Outer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatechartParallelTest {

  private static final Map<String, String> INSIDE =
      Map.of("outer", "P", "left", "WORKING", "right", "WORKING");

  @Test
  void parallelStateIsEnteredWithItsRegionsInDocumentOrder() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton()
            .onEntry(OUTER, Outer.P, "enter P")
            .onEntry(LEFT, Branch.WORKING, "enter left")
            .onEntry(RIGHT, Branch.WORKING, "enter right")
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("go"));

    assertThat(step.effects()).containsExactly("enter P", "enter left", "enter right");
    assertThat(step.configuration().toPlain()).isEqualTo(INSIDE);
  }

  @Test
  void parallelStateIsExitedWithItsRegionsLastRegionFirst() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton()
            .onExit(OUTER, Outer.P, "exit P")
            .onExit(LEFT, Branch.WORKING, "exit left")
            .onExit(RIGHT, Branch.WORKING, "exit right")
            .build();

    Macrostep<String> step = chart.step(chart.configuration(INSIDE), "", new Ev("leave"));

    assertThat(step.effects()).containsExactly("exit right", "exit left", "exit P");
    assertThat(step.configuration().toPlain()).isEqualTo(Map.of("outer", "DONE"));
    assertThat(step.halted()).isTrue();
  }

  @Test
  void irp403bRunsTheParallelStatesTransitionOnceBesideItsRegionsTransition() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton()
            .raiseOnEntry(OUTER, Outer.P, new Ev("event1"))
            .raiseOnEntry(OUTER, Outer.P, new Ev("event2"))
            .onMatching(OUTER, Outer.P, "event1", e -> e.name().equals("event1"))
            .emit("count")
            .internal()
            .onMatching(LEFT, Branch.WORKING, "event2", e -> e.name().equals("event2"))
            .emit("pass")
            .internal()
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("go"));

    assertThat(step.effects()).containsExactly("count", "pass");
  }

  @Test
  void irp403cLetsOneRegionsTransitionPreemptTheParallelStatesOwn() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton()
            .raiseOnEntry(OUTER, Outer.P, new Ev("event1"))
            .onMatching(OUTER, Outer.P, "event1", e -> e.name().equals("event1"))
            .emit("ancestor")
            .to(Outer.DONE)
            .onMatching(LEFT, Branch.WORKING, "event1", e -> e.name().equals("event1"))
            .emit("left")
            .internal()
            .onMatching(RIGHT, Branch.WORKING, "any", e -> true)
            .emit("count")
            .internal()
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("go"));

    assertThat(step.effects()).containsExactly("left", "count");
    assertThat(step.configuration().toPlain()).isEqualTo(INSIDE);
  }

  @Test
  void irp504ExitsAndEntersTheParallelStateAndItsRegionsWhenItsOwnTransitionIsExternal() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton()
            .onExit(OUTER, Outer.WRAP, "exit wrap")
            .onExit(OUTER, Outer.P, "exit P")
            .onExit(LEFT, Branch.WORKING, "exit left")
            .onExit(RIGHT, Branch.WORKING, "exit right")
            .onEntry(OUTER, Outer.P, "enter P")
            .onEntry(LEFT, Branch.WORKING, "enter left")
            .onEntry(RIGHT, Branch.WORKING, "enter right")
            .onMatching(OUTER, Outer.P, "foo", e -> e.name().equals("foo"))
            .emit("foo")
            .to(Outer.P)
            .build();

    Macrostep<String> step = chart.step(chart.configuration(INSIDE), "", new Ev("foo"));

    assertThat(step.effects())
        .containsExactly(
            "exit right", "exit left", "exit P", "foo", "enter P", "enter left", "enter right");
  }

  @Test
  void irp533ExitsTheParallelStateWhenItsInternalTransitionHasItAsTarget() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton()
            .onExit(OUTER, Outer.P, "exit P")
            .onExit(RIGHT, Branch.WORKING, "exit right")
            .onMatching(OUTER, Outer.P, "foo", e -> e.name().equals("foo"))
            .emit("act")
            .within(Outer.P)
            .build();

    Macrostep<String> step = chart.step(chart.configuration(INSIDE), "", new Ev("foo"));

    assertThat(step.effects()).containsExactly("exit right", "exit P", "act");
  }

  @Test
  void irp417RaisesDoneForTheParallelStateWhenEveryRegionIsFinal() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton()
            .completion(done -> new Ev("done." + done.region() + "." + done.state()))
            .always(LEFT, Branch.WORKING)
            .to(Branch.FINISHED)
            .always(RIGHT, Branch.WORKING)
            .to(Branch.FINISHED)
            .onMatching(OUTER, Outer.P, "done", e -> e.name().equals("done.outer.P"))
            .emit("done P")
            .to(Outer.DONE)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("go"));

    assertThat(step.effects()).containsExactly("done P");
    assertThat(step.configuration().toPlain()).isEqualTo(Map.of("outer", "DONE"));
  }

  @Test
  void parallelStateIsNotDoneWhileOneRegionIsNotFinal() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton()
            .completion(done -> new Ev(done.state()))
            .onMatching(OUTER, Outer.P, "done", e -> e.name().equals("P"))
            .emit("parallel done")
            .to(Outer.DONE)
            .build();

    Macrostep<String> step = chart.step(chart.configuration(INSIDE), "", new Ev("finishLeft"));

    assertThat(step.effects()).isEmpty();
    assertThat(step.configuration().get(LEFT)).isEqualTo(Branch.FINISHED);
  }

  @Test
  void irp570RaisesDoneForEachRegionBeforeTheParallelStatesOwn() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton()
            .completion(done -> new Ev("done." + done.region() + "." + done.state()))
            .raiseOnEntry(OUTER, Outer.P, new Ev("finishLeft"))
            .raiseOnEntry(OUTER, Outer.P, new Ev("finishRight"))
            .onMatching(OUTER, Outer.P, "left", e -> e.name().equals("done.left.WHOLE"))
            .emit("left done")
            .internal()
            .onMatching(OUTER, Outer.P, "right", e -> e.name().equals("done.right.WHOLE"))
            .emit("right done")
            .internal()
            .onMatching(OUTER, Outer.P, "parallel", e -> e.name().equals("done.outer.P"))
            .emit("parallel done")
            .to(Outer.DONE)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("go"));

    assertThat(step.effects()).containsExactly("left done", "right done", "parallel done");
  }

  @Test
  void irp576EntersEachRegionAtItsDeclaredInitialState() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton(Outer.START, Branch.FINISHED)
            .raiseOnEntry(LEFT, Branch.FINISHED, new Ev("seen"))
            .onEntry(LEFT, Branch.WHOLE, "enter whole")
            .onMatching(RIGHT, Branch.WORKING, "seen", e -> e.name().equals("seen"))
            .emit("pass")
            .internal()
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("go"));

    assertThat(step.effects()).containsExactly("enter whole", "pass");
    assertThat(step.configuration().toPlain())
        .isEqualTo(Map.of("outer", "P", "left", "FINISHED", "right", "WORKING"));
  }

  @Test
  void initialConfigurationHoldsTheRegionsOfTheParallelInitialState() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton(Outer.P, Branch.WHOLE).build();

    assertThat(chart.initial().toPlain()).isEqualTo(INSIDE);
  }

  @Test
  void configurationOmitsTheRegionsOfTheParallelStateThatIsNotActive() {
    Statechart<Ev, String, String> chart = TestParallelBuilder.skeleton().build();

    assertThat(chart.initial().toPlain()).isEqualTo(Map.of("outer", "START"));
  }

  @Test
  void configurationRefusesTheRegionOfTheParallelStateThatIsNotActive() {
    Statechart<Ev, String, String> chart = TestParallelBuilder.skeleton().build();
    Map<String, String> plain = Map.of("outer", "DONE", "left", "WORKING");

    assertThatThrownBy(() -> chart.configuration(plain))
        .isInstanceOfSatisfying(
            InvalidConfiguration.class,
            invalid -> assertThat(invalid.problems()).containsExactly("region left is not active"));
  }

  @Test
  void configurationRequiresEveryRegionOfTheActiveParallelState() {
    Statechart<Ev, String, String> chart = TestParallelBuilder.skeleton().build();
    Map<String, String> plain = Map.of("outer", "P", "left", "WORKING");

    assertThatThrownBy(() -> chart.configuration(plain))
        .isInstanceOfSatisfying(
            InvalidConfiguration.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly("region right has no known state, got null"));
  }

  @Test
  void inIsFalseForTheRegionWhoseParallelStateIsNotActive() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton()
            .onMatching(OUTER, Outer.START, "probe", e -> e.name().equals("probe"))
            .whenActive(active -> active.in(LEFT, Branch.WORKING))
            .emit("probed")
            .internal()
            .build();

    Macrostep<String> step = chart.step(chart.initial(), "", new Ev("probe"));

    assertThat(step.effects()).isEmpty();
  }

  @Test
  void parallelStateWithoutRegionsIsReported() {
    StatechartBuilder<Ev, String, String> builder =
        TestParallelBuilder.skeleton().parallel(OUTER, Outer.START);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems()).contains("parallel outer.START has no region"));
  }

  @Test
  void parallelStateThatIsCompoundIsReported() {
    StatechartBuilder<Ev, String, String> builder =
        TestParallelBuilder.skeleton().parallel(OUTER, Outer.WRAP, LEFT);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("parallel outer.WRAP is compound, a choice or final"));
  }

  @Test
  void parallelStateNamingAnUnknownRegionIsReported() {
    StatechartBuilder<Ev, String, String> builder =
        TestParallelBuilder.skeleton()
            .parallel(OUTER, Outer.START, new RegionId<>("ghost", Branch.class));

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("parallel outer.START has unknown region ghost"));
  }

  @Test
  void regionClaimedByTwoParallelStatesIsReported() {
    StatechartBuilder<Ev, String, String> builder =
        TestParallelBuilder.skeleton().parallel(OUTER, Outer.START, LEFT);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("region left belongs to more than one parallel"));
  }

  @Test
  void regionInsideItselfIsReported() {
    StatechartBuilder<Ev, String, String> builder =
        TestParallelBuilder.skeleton().parallel(LEFT, Branch.WORKING, OUTER);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid -> assertThat(invalid.problems()).contains("region outer is inside itself"));
  }

  @Test
  void parallelStateIsDrawnWithItsRegionsInside() {
    String drawn = TestParallelBuilder.skeleton().build().render();

    assertThat(drawn)
        .contains("state outer_P {", "state left {", "state right {", "outer_P : P")
        .doesNotContain("    --\n    state left");
  }
}
