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

class ChartStepTest {

  @Test
  void regionMovesOnItsEventAndEmitsTheEffect() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .emit("lit")
            .to(Power.ON)
            .build();

    ChartStep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
    assertThat(step.effects()).containsExactly("lit");
  }

  @Test
  void guardReadsAnotherRegion() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .when((others, event) -> others.get(Lamp.MODE) == Mode.BRIGHT)
            .to(Power.ON)
            .on(Lamp.MODE, Mode.DIM, Tick.class)
            .to(Mode.BRIGHT)
            .build();

    Configuration start = chart.configuration(Map.of("power", "OFF", "mode", "BRIGHT"));

    ChartStep<String> step = chart.step(start, new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
  }

  @Test
  void guardThatDoesNotHoldKeepsTheRegionWhereItIs() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .when((others, event) -> others.get(Lamp.MODE) == Mode.BRIGHT)
            .to(Power.ON)
            .on(Lamp.MODE, Mode.DIM, Tick.class)
            .to(Mode.BRIGHT)
            .build();

    ChartStep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.OFF);
  }

  @Test
  void guardCannotReadTheRegionItsEdgeLeaves() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .when((others, event) -> others.get(Lamp.POWER) == Power.OFF)
            .to(Power.ON)
            .build();
    Configuration start = chart.initial();

    assertThatThrownBy(() -> chart.step(start, new Readings(Mode.DIM), new Press()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void exitEffectsComeBeforeEdgeEffectsEachInDocumentOrder() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
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

    ChartStep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.effects()).containsExactly("left off", "left dim", "on", "bright");
  }

  @Test
  void anEdgeEmitsAnEffectBuiltFromTheEventThatTookIt() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .emitFor(event -> "took " + event)
            .to(Power.ON)
            .build();

    ChartStep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.effects()).containsExactly("took Press[]");
  }

  @Test
  void stayEdgeEmitsItsEffectAndNoExitEffect() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .onExit(Lamp.POWER, Power.OFF, "left off")
            .on(Lamp.POWER, Power.OFF, Press.class)
            .emit("pressed")
            .stay()
            .on(Lamp.POWER, Power.OFF, Tick.class)
            .to(Power.ON)
            .build();

    ChartStep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.OFF);
    assertThat(step.effects()).containsExactly("pressed");
  }

  @Test
  void anExternalSelfEdgeStillExitsItsState() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .onExit(Lamp.POWER, Power.OFF, "left off")
            .on(Lamp.POWER, Power.OFF, Press.class)
            .emit("pressed")
            .to(Power.OFF)
            .on(Lamp.POWER, Power.OFF, Tick.class)
            .to(Power.ON)
            .build();

    ChartStep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.effects()).containsExactly("left off", "pressed");
  }

  @Test
  void parallelRegionsTakeTheSameEventAgainstTheConfigurationFromBeforeTheStep() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .on(Lamp.MODE, Mode.DIM, Press.class)
            .when((others, event) -> others.get(Lamp.POWER) == Power.OFF)
            .to(Mode.BRIGHT)
            .build();

    Configuration after =
        chart.step(chart.initial(), new Readings(Mode.DIM), new Press()).configuration();

    assertThat(after.toPlain()).containsEntry("power", "ON").containsEntry("mode", "BRIGHT");
  }

  @Test
  void raisedEventsRunToCompletionInTheOrderTheyWereRaised() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
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

    ChartStep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.STAGE)).isEqualTo(Stage.HIGH);
    assertThat(step.effects()).containsExactly("on", "tick", "hit");
  }

  @Test
  void cycleOfRaisedEventsStopsAtTheCeilingNamingTheCycle() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
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
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .raise(new Tick())
            .to(Power.ON)
            .raiseCeiling(1)
            .build();

    ChartStep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
  }

  @Test
  void anEventNoEdgeTakesChangesNothing() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .emit("lit")
            .to(Power.ON)
            .build();
    Configuration start = chart.initial();

    ChartStep<String> step = chart.step(start, new Readings(Mode.DIM), new Hit());

    assertThat(step.configuration()).isEqualTo(start);
    assertThat(step.effects()).isEmpty();
  }

  @Test
  void derivedRegionIsReadFromTheFactsWhateverTheEventAndNeverStored() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .derived(Lamp.LEVEL, Readings::level)
            .on(Lamp.POWER, Power.OFF, Event.class)
            .when((others, event) -> others.get(Lamp.LEVEL) == Mode.BRIGHT)
            .to(Power.ON)
            .build();

    ChartStep<String> bright = chart.step(chart.initial(), new Readings(Mode.BRIGHT), new Hit());
    ChartStep<String> dim = chart.step(chart.initial(), new Readings(Mode.DIM), new Hit());

    assertThat(chart.initial().toPlain()).containsOnlyKeys("power");
    assertThat(bright.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
    assertThat(dim.configuration().get(Lamp.POWER)).isEqualTo(Power.OFF);
  }

  @Test
  void guardReadsTheDerivedRegionItNamesAmongSeveral() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .derived(Lamp.LEVEL, Readings::level)
            .derived(Lamp.GLOW, facts -> Mode.BRIGHT)
            .on(Lamp.POWER, Power.OFF, Press.class)
            .when((others, event) -> others.get(Lamp.GLOW) == Mode.BRIGHT)
            .to(Power.ON)
            .build();

    ChartStep<String> step = chart.step(chart.initial(), new Readings(Mode.DIM), new Press());

    assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
  }

  @Test
  void guardCannotReadRegionTheChartDoesNotHave() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .when((others, event) -> others.get(Lamp.MODE) == Mode.BRIGHT)
            .to(Power.ON)
            .build();
    Configuration start = chart.initial();

    assertThatThrownBy(() -> chart.step(start, new Readings(Mode.DIM), new Press()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void derivedRegionNoGuardReadsIsNeverComputed() {
    AtomicInteger computed = new AtomicInteger();
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .derived(
                Lamp.LEVEL,
                facts -> {
                  computed.incrementAndGet();
                  return facts.level();
                })
            .on(Lamp.POWER, Power.OFF, Press.class)
            .when((others, event) -> others.get(Lamp.LEVEL) == Mode.BRIGHT)
            .to(Power.ON)
            .build();

    chart.step(chart.initial(), new Readings(Mode.BRIGHT), new Tick());

    assertThat(computed).hasValue(0);
  }

  @Test
  void derivedRegionTwoGuardsReadIsComputedOnceForTheStep() {
    AtomicInteger computed = new AtomicInteger();
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
            .derived(
                Lamp.LEVEL,
                facts -> {
                  computed.incrementAndGet();
                  return facts.level();
                })
            .on(Lamp.POWER, Power.OFF, Press.class)
            .when((others, event) -> others.get(Lamp.LEVEL) == Mode.BRIGHT)
            .to(Power.ON)
            .on(Lamp.MODE, Mode.DIM, Press.class)
            .when((others, event) -> others.get(Lamp.LEVEL) == Mode.BRIGHT)
            .to(Mode.BRIGHT)
            .build();

    chart.step(chart.initial(), new Readings(Mode.BRIGHT), new Press());

    assertThat(computed).hasValue(1);
  }

  @Test
  void groupThatStaysIsTakenWhereItsGuardHoldsAndMovesNothing() {
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .derived(Lamp.LEVEL, Readings::level)
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .onMatching(
                Lamp.POWER, Set.of(Power.OFF, Power.ON), "probe", event -> event instanceof Hit)
            .when((others, event) -> others.get(Lamp.LEVEL) == Mode.BRIGHT)
            .stay()
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
    Chart<Event, Readings, String> chart =
        Chart.<Event, Readings, String>builder()
            .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
            .on(Lamp.POWER, Power.OFF, Press.class)
            .to(Power.ON)
            .build();
    Configuration start = chart.initial();

    assertThatThrownBy(() -> chart.takes(Lamp.MODE, start, new Readings(Mode.DIM), new Press()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
