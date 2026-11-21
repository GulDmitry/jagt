package dev.jagt.statechart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.statechart.Lamp.Event;
import dev.jagt.statechart.Lamp.Hit;
import dev.jagt.statechart.Lamp.Mode;
import dev.jagt.statechart.Lamp.Power;
import dev.jagt.statechart.Lamp.Press;
import dev.jagt.statechart.Lamp.Readings;
import dev.jagt.statechart.Lamp.Stage;
import dev.jagt.statechart.Lamp.Tick;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MacrostepTest {

  private final StatechartBuilder<Event, Readings, String> powerOnly =
      Statechart.<Event, Readings, String>builder().region(Lamp.POWER, Power.OFF, Set.of(Power.ON));

  private final StatechartBuilder<Event, Readings, String> powerAndMode =
      Statechart.<Event, Readings, String>builder()
          .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
          .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT));

  private final Statechart<Event, Readings, String> litByPress =
      Statechart.<Event, Readings, String>builder()
          .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
          .on(Lamp.POWER, Power.OFF, Press.class)
          .emit("lit")
          .to(Power.ON)
          .build();

  private final Statechart<Event, Readings, String> pressMovesPower =
      Statechart.<Event, Readings, String>builder()
          .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
          .on(Lamp.POWER, Power.OFF, Press.class)
          .to(Power.ON)
          .build();

  @Test
  void regionMovesOnItsEventAndEmitsTheEffect() {

    Macrostep<String> step =
        litByPress.step(litByPress.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
    assertThat(step.effects()).containsExactly("lit");
  }

  @ParameterizedTest
  @CsvSource({"BRIGHT, ON", "DIM, OFF"})
  void guardOnAnotherRegionDecidesWhetherTheTransitionIsTaken(String mode, String power) {
    Statechart<Event, Readings, String> chart =
        powerAndMode
            .on(Lamp.POWER, Power.OFF, Press.class)
            .when((active, event) -> active.state(Lamp.MODE) == Mode.BRIGHT)
            .to(Power.ON)
            .on(Lamp.MODE, Mode.DIM, Tick.class)
            .to(Mode.BRIGHT)
            .build();
    Configuration start = chart.configuration(Map.of("power", "OFF", "mode", mode));

    Macrostep<String> step = chart.step(start, new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.valueOf(power));
  }

  @Test
  void exitEffectsComeBeforeTransitionEffectsEachInDocumentOrder() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
            .onExit(Lamp.POWER, Power.OFF, "left off")
            .onExit(Lamp.MODE, Mode.DIM, "left dim")
            .on(Lamp.POWER, Power.OFF, Press.class)
            .emit("on")
            .to(Power.ON)
            .on(Lamp.MODE, Mode.DIM, Press.class)
            .emit("bright")
            .to(Mode.BRIGHT)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.effects()).containsExactly("left off", "left dim", "on", "bright");
  }

  @Test
  void transitionEmitsAnEffectBuiltFromTheEventThatTookIt() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .emitFor(event -> "took " + event)
            .to(Power.ON)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.effects()).containsExactly("took Press[]");
  }

  @Test
  void internalTransitionEmitsItsEffectAndNoExitEffect() {
    Statechart<Event, Readings, String> chart =
        powerOnly
            .onExit(Lamp.POWER, Power.OFF, "left off")
            .on(Lamp.POWER, Power.OFF, Press.class)
            .emit("pressed")
            .internal()
            .on(Lamp.POWER, Power.OFF, Tick.class)
            .to(Power.ON)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.OFF);
    assertThat(step.effects()).containsExactly("pressed");
  }

  @Test
  void anExternalSelfTransitionStillExitsItsState() {
    Statechart<Event, Readings, String> chart =
        powerOnly
            .onExit(Lamp.POWER, Power.OFF, "left off")
            .on(Lamp.POWER, Power.OFF, Press.class)
            .emit("pressed")
            .to(Power.OFF)
            .on(Lamp.POWER, Power.OFF, Tick.class)
            .to(Power.ON)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.effects()).containsExactly("left off", "pressed");
  }

  @Test
  void parallelRegionsTakeTheSameEventAgainstTheConfigurationFromBeforeTheStep() {
    Statechart<Event, Readings, String> chart =
        powerAndMode
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .on(Lamp.MODE, Mode.DIM, Press.class)
            .when((active, event) -> active.state(Lamp.POWER) == Power.OFF)
            .to(Mode.BRIGHT)
            .build();

    Configuration after =
        chart.step(chart.initial(), new Readings(Mode.DIM), new Press()).configuration();

    assertThat(after.toPlain()).containsEntry("power", "ON").containsEntry("mode", "BRIGHT");
  }

  @Test
  void raisedEventsRunToCompletionInTheOrderTheyWereRaised() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .emit("on")
            .raise(new Tick())
            .raise(new Hit())
            .to(Power.ON)
            .on(Lamp.STAGE, Stage.LOW, Tick.class)
            .emit("tick")
            .to(Stage.MID)
            .on(Lamp.STAGE, Stage.MID, Hit.class)
            .emit("hit")
            .to(Stage.HIGH)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.STAGE)).isEqualTo(Stage.HIGH);
    assertThat(step.effects()).containsExactly("on", "tick", "hit");
  }

  @Test
  void cycleOfRaisedEventsStopsAtTheCeilingNamingTheCycle() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF)
            .on(Lamp.POWER, Power.OFF, Press.class)
            .raise(new Press())
            .to(Power.ON)
            .on(Lamp.POWER, Power.ON, Press.class)
            .raise(new Press())
            .to(Power.OFF)
            .raiseCeiling(5)
            .build();
    Configuration start = chart.initial();

    assertThatThrownBy(() -> chart.step(start, new Readings(Mode.DIM), new Press()))
        .isInstanceOf(RaiseCeilingExceeded.class)
        .hasMessageContaining("power OFF->ON")
        .hasMessageContaining("power ON->OFF");
  }

  @Test
  void cycleRaisingExactlyTheCeilingIsAllowed() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
            .on(Lamp.STAGE, Stage.LOW, Press.class)
            .raise(new Tick())
            .to(Stage.MID)
            .on(Lamp.STAGE, Stage.MID, Tick.class)
            .to(Stage.HIGH)
            .raiseCeiling(1)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.STAGE)).isEqualTo(Stage.HIGH);
  }

  @Test
  void anEventNoTransitionTakesChangesNothing() {
    Configuration start = litByPress.initial();

    Macrostep<String> step = litByPress.step(start, new Readings(Mode.DIM), new Hit());

    assertThat(step.configuration()).isEqualTo(start);
    assertThat(step.effects()).isEmpty();
  }

  @Test
  void datumIsReadFromTheDataModelWhateverTheEventAndNeverInTheConfiguration() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .datum(Lamp.LEVEL, Readings::level)
            .on(Lamp.POWER, Power.OFF, Event.class)
            .when((active, event) -> active.datum(Lamp.LEVEL) == Mode.BRIGHT)
            .to(Power.ON)
            .build();

    Macrostep<String> bright = chart.step(chart.initial(), new Readings(Mode.BRIGHT), new Hit());
    Macrostep<String> dim = chart.step(chart.initial(), new Readings(Mode.DIM), new Hit());

    assertThat(chart.initial().toPlain()).containsOnlyKeys("power");
    assertThat(bright.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
    assertThat(dim.configuration().get(Lamp.POWER)).isEqualTo(Power.OFF);
  }

  @Test
  void guardReadsTheDatumItNamesAmongSeveral() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .datum(Lamp.LEVEL, Readings::level)
            .datum(Lamp.GLOW, facts -> Mode.BRIGHT)
            .on(Lamp.POWER, Power.OFF, Press.class)
            .when((active, event) -> active.datum(Lamp.GLOW) == Mode.BRIGHT)
            .to(Power.ON)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
  }

  @Test
  void guardCannotReadRegionTheChartDoesNotHave() {
    Statechart<Event, Readings, String> chart =
        powerOnly
            .on(Lamp.POWER, Power.OFF, Press.class)
            .when((active, event) -> active.state(Lamp.MODE) == Mode.BRIGHT)
            .to(Power.ON)
            .build();
    Configuration start = chart.initial();

    assertThatThrownBy(() -> chart.step(start, new Readings(Mode.DIM), new Press()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void datumNoGuardReadsIsNeverComputed() {
    AtomicInteger computed = new AtomicInteger();
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .datum(
                Lamp.LEVEL,
                facts -> {
                  computed.incrementAndGet();
                  return facts.level();
                })
            .on(Lamp.POWER, Power.OFF, Press.class)
            .when((active, event) -> active.datum(Lamp.LEVEL) == Mode.BRIGHT)
            .to(Power.ON)
            .build();

    chart.step(chart.initial(), new Readings(Mode.BRIGHT), new Tick());

    assertThat(computed).hasValue(0);
  }

  @Test
  void datumTwoGuardsReadIsComputedOnceForTheMacrostep() {
    AtomicInteger computed = new AtomicInteger();
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
            .datum(
                Lamp.LEVEL,
                facts -> {
                  computed.incrementAndGet();
                  return facts.level();
                })
            .on(Lamp.POWER, Power.OFF, Press.class)
            .when((active, event) -> active.datum(Lamp.LEVEL) == Mode.BRIGHT)
            .to(Power.ON)
            .on(Lamp.MODE, Mode.DIM, Press.class)
            .when((active, event) -> active.datum(Lamp.LEVEL) == Mode.BRIGHT)
            .to(Mode.BRIGHT)
            .build();

    chart.step(chart.initial(), new Readings(Mode.BRIGHT), new Press());

    assertThat(computed).hasValue(1);
  }

  @Test
  void groupThatStaysIsTakenWhereItsGuardHoldsAndMovesNothing() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .datum(Lamp.LEVEL, Readings::level)
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .onMatching(
                Lamp.POWER, Set.of(Power.OFF, Power.ON), "probe", event -> event instanceof Hit)
            .when((active, event) -> active.datum(Lamp.LEVEL) == Mode.BRIGHT)
            .internal()
            .build();
    Configuration start = chart.initial();

    assertThat(chart.takes(Lamp.POWER, start, new Readings(Mode.BRIGHT), new Hit())).isTrue();
    assertThat(chart.takes(Lamp.POWER, start, new Readings(Mode.DIM), new Hit())).isFalse();
    assertThat(chart.step(start, new Readings(Mode.BRIGHT), new Hit()).configuration())
        .isEqualTo(start);
    assertThat(chart.step(start, new Readings(Mode.BRIGHT), new Hit()).effects()).isEmpty();
  }

  @Test
  void takesCannotBeAskedOfRegionTheChartDoesNotStore() {
    Configuration start = pressMovesPower.initial();

    assertThatThrownBy(
            () -> pressMovesPower.takes(Lamp.MODE, start, new Readings(Mode.DIM), new Press()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void alwaysTransitionIsTakenOnceTheEventIsSpent() {
    Statechart<Event, Readings, String> chart =
        powerOnly.always(Lamp.POWER, Power.OFF).emit("auto").to(Power.ON).build();

    Macrostep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Tick());

    assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
    assertThat(step.effects()).containsExactly("auto");
  }

  @Test
  void alwaysTransitionWaitsWhileItsGuardRefuses() {
    Statechart<Event, Readings, String> chart =
        powerAndMode
            .always(Lamp.POWER, Power.OFF)
            .whenActive(active -> active.state(Lamp.MODE) == Mode.BRIGHT)
            .to(Power.ON)
            .on(Lamp.MODE, Mode.DIM, Press.class)
            .to(Mode.BRIGHT)
            .build();

    Macrostep<String> idle = chart.step(chart.initial(), new Readings(Mode.DIM), new Tick());
    Macrostep<String> pressed = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(idle.configuration().get(Lamp.POWER)).isEqualTo(Power.OFF);
    assertThat(pressed.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
  }

  @Test
  void alwaysCycleStopsAtTheCeilingNamingTheCycle() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF)
            .always(Lamp.POWER, Power.OFF)
            .to(Power.ON)
            .always(Lamp.POWER, Power.ON)
            .to(Power.OFF)
            .raiseCeiling(5)
            .build();
    Configuration start = chart.initial();

    assertThatThrownBy(() -> chart.step(start, new Readings(Mode.DIM), new Tick()))
        .isInstanceOf(RaiseCeilingExceeded.class)
        .hasMessageContaining("power OFF->ON")
        .hasMessageContaining("power ON->OFF");
  }

  @Test
  void alwaysTransitionRefusesGuardThatReadsEvent() {
    TransitionBuilder<Event, Readings, String, Power> always =
        powerOnly.always(Lamp.POWER, Power.OFF);

    assertThatThrownBy(() -> always.when((active, event) -> true))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void alwaysTransitionRefusesEffectThatReadsEvent() {
    TransitionBuilder<Event, Readings, String, Power> always =
        powerOnly.always(Lamp.POWER, Power.OFF);

    assertThatThrownBy(() -> always.emitFor(event -> "x"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void entryRaiseRunsAfterTheEntryEffects() {
    Statechart<Event, Readings, String> chart =
        powerOnly
            .onEntry(Lamp.POWER, Power.ON, "entered")
            .raiseOnEntry(Lamp.POWER, Power.ON, new Tick())
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .on(Lamp.POWER, Power.ON, Tick.class)
            .emit("ticked")
            .internal()
            .build();

    Macrostep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.effects()).containsExactly("entered", "ticked");
  }

  @Test
  void everyRegionFinalRaisesTheChartsCompletionAndHalts() {
    Statechart<Event, Readings, String> chart =
        powerAndMode
            .completion(done -> done instanceof Completed.WholeChart ? new Tick() : new Hit())
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .on(Lamp.MODE, Mode.DIM, Press.class)
            .to(Mode.BRIGHT)
            .on(Lamp.POWER, Power.ON, Tick.class)
            .emit("chart done")
            .internal()
            .build();

    Macrostep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.effects()).containsExactly("chart done");
    assertThat(step.halted()).isTrue();
  }

  @Test
  void chartKeepsRunningWhileRegionIsOpen() {
    Statechart<Event, Readings, String> chart =
        powerAndMode
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .on(Lamp.MODE, Mode.DIM, Hit.class)
            .to(Mode.BRIGHT)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
    assertThat(step.halted()).isFalse();
  }

  @Test
  void configurationKeepsItsRegionsInDeclarationOrderWhicheverRegionMoved() {
    Statechart<Event, Readings, String> chart =
        Statechart.<Event, Readings, String>builder()
            .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.STAGE, Stage.LOW, Press.class)
            .to(Stage.MID)
            .on(Lamp.STAGE, Stage.MID, Press.class)
            .to(Stage.HIGH)
            .on(Lamp.POWER, Power.OFF, Tick.class)
            .to(Power.ON)
            .build();

    Macrostep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().toPlain().keySet()).containsExactly("stage", "power");
  }
}
