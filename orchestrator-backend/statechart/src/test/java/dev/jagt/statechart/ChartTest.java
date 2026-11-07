package dev.jagt.statechart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.statechart.Lamp.Event;
import dev.jagt.statechart.Lamp.Facts;
import dev.jagt.statechart.Lamp.Gear;
import dev.jagt.statechart.Lamp.Hit;
import dev.jagt.statechart.Lamp.Mode;
import dev.jagt.statechart.Lamp.Power;
import dev.jagt.statechart.Lamp.Press;
import dev.jagt.statechart.Lamp.Stage;
import dev.jagt.statechart.Lamp.Tick;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChartTest {

  @Test
  void aStateNothingReachesIsReported() {
    ChartBuilder<Event, Facts, String> builder =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.MID, Stage.HIGH))
            .on(Lamp.STAGE, Stage.LOW, Press.class)
            .to(Stage.MID);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            ChartInvalid.class,
            invalid ->
                assertThat(invalid.problems()).containsExactly("state stage.HIGH is unreachable"));
  }

  @Test
  void aNonFinalStateWithNoEdgeOutIsReported() {
    ChartBuilder<Event, Facts, String> builder =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.POWER, Power.OFF)
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            ChartInvalid.class,
            invalid ->
                assertThat(invalid.problems()).containsExactly("state power.ON has no way out"));
  }

  @Test
  void aRegionDeclaredTwiceIsReported() {
    ChartBuilder<Event, Facts, String> builder =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            ChartInvalid.class,
            invalid ->
                assertThat(invalid.problems()).contains("region power is declared more than once"));
  }

  @Test
  void aEdgeOnADerivedRegionIsReported() {
    ChartBuilder<Event, Facts, String> builder =
        Chart.<Event, Facts, String>builder()
            .derived(Lamp.LEVEL, Facts::level)
            .on(Lamp.LEVEL, Mode.DIM, Press.class)
            .to(Mode.BRIGHT);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            ChartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly("derived region level has a transition"));
  }

  @Test
  void aDerivedRegionDeclaredTwiceIsReported() {
    ChartBuilder<Event, Facts, String> builder =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .derived(Lamp.LEVEL, Facts::level)
            .derived(Lamp.LEVEL, Facts::level)
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            ChartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly("region level is declared more than once"));
  }

  @Test
  void aRegionIsJudgedByItsOwnEdgesAlone() {
    ChartBuilder<Event, Facts, String> builder =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
            .region(Lamp.GLOW, Mode.DIM, Set.of(Mode.BRIGHT))
            .on(Lamp.MODE, Mode.DIM, Tick.class)
            .to(Mode.BRIGHT);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            ChartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly(
                        "state glow.DIM has no way out", "state glow.BRIGHT is unreachable"));
  }

  @Test
  void aStateOnlyAnUnreachableStateLeadsToIsUnreachable() {
    ChartBuilder<Event, Facts, String> builder =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.LOW, Stage.HIGH))
            .on(Lamp.STAGE, Stage.MID, Press.class)
            .to(Stage.HIGH);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            ChartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly(
                        "state stage.MID is unreachable", "state stage.HIGH is unreachable"));
  }

  @Test
  void everyProblemIsReportedAtOnce() {
    ChartBuilder<Event, Facts, String> builder =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of())
            .on(Lamp.STAGE, Stage.LOW, Press.class)
            .to(Stage.MID);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            ChartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly(
                        "state stage.MID has no way out",
                        "state stage.HIGH has no way out",
                        "state stage.HIGH is unreachable"));
  }

  @Test
  void aConfigurationRoundTripsThroughItsPlainData() {
    Chart<Event, Facts, String> chart =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .on(Lamp.MODE, Mode.DIM, Tick.class)
            .to(Mode.BRIGHT)
            .build();
    Configuration moved =
        chart.step(chart.initial(), new Facts(Mode.DIM), new Tick()).configuration();

    Configuration restored = chart.configuration(moved.toPlain());

    assertThat(moved.toPlain()).containsEntry("power", "OFF").containsEntry("mode", "BRIGHT");
    assertThat(restored).isEqualTo(moved).hasSameHashCodeAs(moved).isNotEqualTo(chart.initial());
  }

  @Test
  void aConfigurationNamingAnUnknownStateIsRejected() {
    Chart<Event, Facts, String> chart =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .build();
    Map<String, String> plain = Map.of("power", "BROKEN");

    assertThatThrownBy(() -> chart.configuration(plain))
        .isInstanceOfSatisfying(
            InvalidConfiguration.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly("region power has no known state, got BROKEN"));
  }

  @Test
  void aConfigurationNamingAnUnknownRegionIsRejected() {
    Chart<Event, Facts, String> chart =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .build();
    Map<String, String> plain = Map.of("power", "OFF", "mode", "DIM");

    assertThatThrownBy(() -> chart.configuration(plain))
        .isInstanceOfSatisfying(
            InvalidConfiguration.class,
            invalid -> assertThat(invalid.problems()).containsExactly("no stored region mode"));
  }

  @Test
  void aConfigurationCannotBeAskedForARegionTheChartDoesNotStore() {
    Chart<Event, Facts, String> chart =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .build();
    Configuration start = chart.initial();

    assertThatThrownBy(() -> start.get(Lamp.MODE)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theRenderingIsAStableMermaidDiagramOfEveryRegion() {
    Chart<Event, Facts, String> chart =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
            .derived(Lamp.LEVEL, Facts::level)
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .on(Lamp.POWER, Power.ON, Press.class)
            .to(Power.OFF)
            .on(Lamp.MODE, Mode.DIM, Tick.class)
            .to(Mode.BRIGHT)
            .build();

    assertThat(chart.render())
        .isEqualTo(
            """
            stateDiagram-v2
                state power {
                    state "OFF" as power_OFF
                    state "ON" as power_ON
                    [*] --> power_OFF
                    power_OFF --> power_ON : Press
                    power_ON --> power_OFF : Press
                    power_ON --> [*]
                }
                --
                state mode {
                    state "DIM" as mode_DIM
                    state "BRIGHT" as mode_BRIGHT
                    [*] --> mode_DIM
                    mode_DIM --> mode_BRIGHT : Tick
                    mode_BRIGHT --> [*]
                }
                %% derived level
            """);
  }

  @Test
  void anEdgeDeclaredFromASetIsDrawnAsOneLabelledEdgeFromTheSet() {
    Chart<Event, Facts, String> chart =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.STAGE, Stage.LOW)
            .on(Lamp.STAGE, Stage.LOW, Press.class)
            .to(Stage.MID)
            .onMatching(Lamp.STAGE, Set.of(Stage.LOW, Stage.MID), "tick", Tick.class::isInstance)
            .to(Stage.HIGH)
            .onMatching(Lamp.STAGE, Set.of(Stage.HIGH), "hit", Hit.class::isInstance)
            .to(Stage.LOW)
            .build();

    assertThat(chart.render())
        .isEqualTo(
            """
            stateDiagram-v2
                state stage {
                    state "LOW" as stage_LOW
                    state "MID" as stage_MID
                    state "HIGH" as stage_HIGH
                    [*] --> stage_LOW
                    stage_LOW --> stage_MID : Press
                    state "all but HIGH" as stage_from_1
                    stage_from_1 --> stage_HIGH : tick
                    stage_HIGH --> stage_LOW : hit
                }
            """);
  }

  @Test
  void anEdgeDeclaredFromAFewOfManyStatesNamesThoseStates() {
    Chart<Event, Facts, String> chart =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.GEAR, Gear.P, Set.of(Gear.D))
            .on(Lamp.GEAR, Gear.P, Press.class)
            .to(Gear.R)
            .onMatching(Lamp.GEAR, Set.of(Gear.P, Gear.R), "tick", Tick.class::isInstance)
            .to(Gear.N)
            .on(Lamp.GEAR, Gear.N, Hit.class)
            .to(Gear.D)
            .build();

    assertThat(chart.render())
        .isEqualTo(
            """
            stateDiagram-v2
                state gear {
                    state "P" as gear_P
                    state "R" as gear_R
                    state "N" as gear_N
                    state "D" as gear_D
                    [*] --> gear_P
                    gear_P --> gear_R : Press
                    state "P, R" as gear_from_1
                    gear_from_1 --> gear_N : tick
                    gear_N --> gear_D : Hit
                    gear_D --> [*]
                }
            """);
  }

  @Test
  void anEdgeDeclaredFromEveryStateIsDrawnOnce() {
    Chart<Event, Facts, String> chart =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.STAGE, Stage.LOW)
            .on(Lamp.STAGE, Stage.LOW, Press.class)
            .to(Stage.MID)
            .onMatching(
                Lamp.STAGE,
                Set.of(Stage.LOW, Stage.MID, Stage.HIGH),
                "tick",
                Tick.class::isInstance)
            .to(Stage.HIGH)
            .build();

    assertThat(chart.render())
        .isEqualTo(
            """
            stateDiagram-v2
                state stage {
                    state "LOW" as stage_LOW
                    state "MID" as stage_MID
                    state "HIGH" as stage_HIGH
                    [*] --> stage_LOW
                    stage_LOW --> stage_MID : Press
                    state "all" as stage_from_1
                    stage_from_1 --> stage_HIGH : tick
                }
            """);
  }

  @Test
  void anEdgeDeclaredFromASetStaysInTheSourceThatIsItsTarget() {
    Chart<Event, Facts, String> chart =
        Chart.<Event, Facts, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
            .onMatching(Lamp.STAGE, Set.of(Stage.LOW, Stage.MID), "tick", Tick.class::isInstance)
            .emit("moved")
            .to(Stage.MID)
            .on(Lamp.STAGE, Stage.MID, Press.class)
            .to(Stage.HIGH)
            .build();

    ChartStep<String> step =
        chart.step(chart.configuration(Map.of("stage", "MID")), new Facts(Mode.DIM), new Tick());

    assertThat(step.effects()).containsExactly("moved");
  }
}
