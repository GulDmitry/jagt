package dev.jagt.statechart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.statechart.Lamp.Event;
import dev.jagt.statechart.Lamp.Mode;
import dev.jagt.statechart.Lamp.Power;
import dev.jagt.statechart.Lamp.Press;
import dev.jagt.statechart.Lamp.Readings;
import dev.jagt.statechart.Lamp.Stage;
import dev.jagt.statechart.Lamp.Tick;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StatechartChoiceTest {

  private final Statechart<Event, Readings, String> brightGoesHigh =
      Statechart.<Event, Readings, String>builder()
          .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
          .datum(Lamp.LEVEL, Readings::level)
          .onExit(Lamp.STAGE, Stage.LOW, "exitLow")
          .onEntry(Lamp.STAGE, Stage.MID, "enterMid")
          .onEntry(Lamp.STAGE, Stage.HIGH, "enterHigh")
          .onEntry(Lamp.STAGE, Stage.LOW, "enterLow")
          .choice(Lamp.STAGE, Stage.MID)
          .branch(active -> active.datum(Lamp.LEVEL) == Mode.BRIGHT, Stage.HIGH)
          .otherwise(Stage.LOW)
          .on(Lamp.STAGE, Stage.LOW, Press.class)
          .emit("pressed")
          .to(Stage.MID)
          .build();

  @Test
  void choiceContinuesToTheFirstBranchWhoseGuardAllows() {
    Macrostep<String> step =
        brightGoesHigh.step(brightGoesHigh.initial(), new Readings(Mode.BRIGHT), new Press());

    assertThat(step.configuration().get(Lamp.STAGE)).isEqualTo(Stage.HIGH);
  }

  @Test
  void choiceTakesOtherwiseWhenNoBranchAllows() {
    Macrostep<String> step =
        brightGoesHigh.step(brightGoesHigh.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.STAGE)).isEqualTo(Stage.LOW);
  }

  @Test
  void choiceBranchesAreTriedInDeclarationOrder() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
            .choice(Lamp.STAGE, Stage.MID)
            .branch(active -> true, Stage.HIGH)
            .branch(active -> true, Stage.LOW)
            .otherwise(Stage.LOW)
            .on(Lamp.STAGE, Stage.LOW, Press.class)
            .to(Stage.MID)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.STAGE)).isEqualTo(Stage.HIGH);
  }

  @Test
  void choiceIsPassedThroughSoOnlyTheBranchIsEntered() {
    Macrostep<String> step =
        brightGoesHigh.step(brightGoesHigh.initial(), new Readings(Mode.BRIGHT), new Press());

    assertThat(step.effects()).containsExactly("exitLow", "pressed", "enterHigh");
  }

  @Test
  void choiceIsNeverPartOfStableConfiguration() {
    Map<String, String> plain = Map.of("stage", "MID");

    assertThatThrownBy(() -> brightGoesHigh.configuration(plain))
        .isInstanceOf(InvalidConfiguration.class);
  }

  @Test
  void choiceWithoutOtherwiseIsReported() {
    StatechartBuilder<Event, Readings, String> builder =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
            .on(Lamp.STAGE, Stage.LOW, Press.class)
            .to(Stage.MID);
    builder.choice(Lamp.STAGE, Stage.MID).branch(active -> true, Stage.HIGH);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly("choice stage.MID has no otherwise"));
  }

  @Test
  void choiceDeclaredWithoutBranchesIsReported() {
    StatechartBuilder<Event, Readings, String> builder =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
            .on(Lamp.STAGE, Stage.LOW, Press.class)
            .to(Stage.HIGH);
    builder.choice(Lamp.STAGE, Stage.MID);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems()).contains("choice stage.MID has no otherwise"));
  }

  @Test
  void choiceAsHistoryDefaultIsReported() {
    StatechartBuilder<Event, Readings, String> builder =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
            .compound(Lamp.STAGE, Stage.LOW, Stage.HIGH, Stage.MID, Stage.HIGH)
            .choice(Lamp.STAGE, Stage.MID)
            .otherwise(Stage.HIGH)
            .history(Lamp.STAGE, Stage.LOW, History.SHALLOW, Stage.MID);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("choice stage.MID is the default of a history"));
  }

  @Test
  void choiceAsRegionInitialStateIsReported() {
    StatechartBuilder<Event, Readings, String> builder =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.STAGE, Stage.MID, Set.of(Stage.HIGH))
            .choice(Lamp.STAGE, Stage.MID)
            .otherwise(Stage.HIGH);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid -> assertThat(invalid.problems()).contains("choice stage.MID is initial"));
  }

  @Test
  void choiceWithTransitionOutIsReported() {
    StatechartBuilder<Event, Readings, String> builder =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
            .choice(Lamp.STAGE, Stage.MID)
            .otherwise(Stage.HIGH)
            .on(Lamp.STAGE, Stage.LOW, Press.class)
            .to(Stage.MID)
            .on(Lamp.STAGE, Stage.MID, Tick.class)
            .to(Stage.HIGH);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems()).contains("choice stage.MID has a transition"));
  }

  @Test
  void finalChoiceIsReported() {
    StatechartBuilder<Event, Readings, String> builder =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.MID, Stage.HIGH))
            .choice(Lamp.STAGE, Stage.MID)
            .otherwise(Stage.HIGH)
            .on(Lamp.STAGE, Stage.LOW, Press.class)
            .to(Stage.MID);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid -> assertThat(invalid.problems()).contains("choice stage.MID is final"));
  }

  @Test
  void choicesLeadingBackToEachOtherAreReported() {
    StatechartBuilder<Event, Readings, String> builder =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
            .choice(Lamp.STAGE, Stage.MID)
            .otherwise(Stage.HIGH)
            .choice(Lamp.STAGE, Stage.HIGH)
            .otherwise(Stage.MID)
            .on(Lamp.STAGE, Stage.LOW, Press.class)
            .to(Stage.MID);

    assertThatThrownBy(builder::build)
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains(
                        "choice stage.MID leads back to itself",
                        "choice stage.HIGH leads back to itself"));
  }

  @Test
  void choiceGuardReadsTheConfigurationAfterTheTransitionLeftItsSource() {
    Statechart<Event, Readings, String> leavingLowSendsItBackToLow =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
            .onExit(Lamp.STAGE, Stage.LOW, "exitLow")
            .onEntry(Lamp.STAGE, Stage.LOW, "enterLow")
            .choice(Lamp.STAGE, Stage.MID)
            .branch(active -> active.in(Lamp.STAGE, Stage.LOW), Stage.HIGH)
            .otherwise(Stage.LOW)
            .on(Lamp.STAGE, Stage.LOW, Press.class)
            .emit("pressed")
            .to(Stage.MID)
            .build();

    Macrostep<String> step =
        leavingLowSendsItBackToLow.step(
            leavingLowSendsItBackToLow.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.effects()).containsExactly("exitLow", "pressed", "enterLow");
    assertThat(step.configuration().get(Lamp.STAGE)).isEqualTo(Stage.LOW);
  }

  @Test
  void choiceIsDrawnWithItsBranches() {
    String drawn = brightGoesHigh.render();

    assertThat(drawn)
        .contains(
            "state stage_MID <<choice>>",
            "stage_MID --> stage_HIGH : [branch]",
            "stage_MID --> stage_LOW : [otherwise]");
  }

  @Test
  void doActivityStartsOnEntryAndStopsOnExit() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF)
            .doActivity(Lamp.POWER, Power.ON, "start", "stop")
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .on(Lamp.POWER, Power.ON, Tick.class)
            .to(Power.OFF)
            .build();

    Macrostep<String> started = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());
    Macrostep<String> stopped =
        chart.step(started.configuration(), new Readings(Mode.DIM), new Tick());

    assertThat(started.effects()).containsExactly("start");
    assertThat(stopped.effects()).containsExactly("stop");
  }
}
