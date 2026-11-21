package dev.jagt.statechart;

import static dev.jagt.statechart.TestIrpBuilder.NODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.statechart.Lamp.Event;
import dev.jagt.statechart.Lamp.Mode;
import dev.jagt.statechart.Lamp.Power;
import dev.jagt.statechart.Lamp.Press;
import dev.jagt.statechart.Lamp.Readings;
import dev.jagt.statechart.Lamp.Stage;
import dev.jagt.statechart.TestIrpBuilder.Ev;
import dev.jagt.statechart.TestIrpBuilder.Node;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StatechartStructureTest {

  @Test
  void compoundWhoseInitialIsNotItsChildIsReported() {
    StatechartBuilder<Ev, String, String> builder =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .compound(NODE, Node.S0, Node.S1, Node.S01, Node.S02);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("compound node.S0 has initial S1 that is not its child"));
  }

  @Test
  void stateChildOfTwoCompoundsIsReported() {
    StatechartBuilder<Ev, String, String> builder =
        TestIrpBuilder.skeleton()
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .compound(NODE, Node.S1, Node.S11, Node.S11, Node.S02);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems()).contains("state node.S02 has more than one parent"));
  }

  @Test
  void stateThatIsItsOwnAncestorIsReported() {
    StatechartBuilder<Ev, String, String> builder =
        Statechart.<Ev, String, String>builder()
            .region(NODE, Node.S0, TestIrpBuilder.finals())
            .compound(NODE, Node.S0, Node.S01, Node.S01)
            .compound(NODE, Node.S01, Node.S0, Node.S0);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems()).contains("state node.S0 is its own ancestor"));
  }

  @Test
  void compoundDeclaredFromSetEntersItsFirstChildInDocumentOrder() {
    Statechart<Event, Readings, String> chart = TestStageBuilder.compoundLow();

    assertThat(chart.initial().get(Lamp.STAGE)).isEqualTo(Stage.MID);
  }

  @Test
  void compoundStateIsDrawnOnceWithItsChildrenInside() {
    Statechart<Event, Readings, String> chart = TestStageBuilder.compoundLow();

    assertThat(chart.render())
        .isEqualTo(
            """
            stateDiagram-v2
                state stage {
                    state stage_LOW {
                        state "MID" as stage_MID
                        state "HIGH" as stage_HIGH
                        [*] --> stage_MID
                        stage_HIGH --> [*]
                    }
                    stage_LOW : LOW
                    [*] --> stage_LOW
                    stage_MID --> stage_HIGH : Press
                }
            """);
  }

  @Test
  void guardReadingDatumTheChartNeverDeclaredFailsNamingIt() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .whenActive(active -> active.datum(Lamp.LEVEL) == Mode.DIM)
            .to(Power.ON)
            .build();
    Configuration start = chart.initial();
    Readings readings = new Readings(Mode.DIM);

    assertThatThrownBy(() -> chart.step(start, readings, new Press()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("level");
  }

  @Test
  void regionTheChartNeverDeclaredCannotBeLookedUp() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .build();

    assertThatThrownBy(() -> chart.byName("ghost"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ghost");
  }
}
