package dev.jagt.statechart;

import dev.jagt.statechart.Lamp.Event;
import dev.jagt.statechart.Lamp.Hit;
import dev.jagt.statechart.Lamp.Mode;
import dev.jagt.statechart.Lamp.Power;
import dev.jagt.statechart.Lamp.Press;
import dev.jagt.statechart.Lamp.Stage;
import dev.jagt.statechart.Lamp.Tick;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepTest {

    @Test
    void aRegionMovesOnItsEventAndEmitsTheEffect() {
        Chart<Event, String> chart = Chart.<Event, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .on(Lamp.POWER, Power.OFF, Press.class).emit("lit").to(Power.ON)
                .build();

        Step<String> step = chart.step(chart.initial(), new Press());

        assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
        assertThat(step.effects()).containsExactly("lit");
    }

    @Test
    void aGuardReadsAnotherRegion() {
        Chart<Event, String> chart = Chart.<Event, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
                .on(Lamp.POWER, Power.OFF, Press.class).when((others, event) -> others.get(Lamp.MODE) == Mode.BRIGHT)
                .to(Power.ON)
                .on(Lamp.MODE, Mode.DIM, Tick.class).to(Mode.BRIGHT)
                .build();

        Step<String> step = chart.step(chart.configuration(Map.of("power", "OFF", "mode", "BRIGHT")), new Press());

        assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
    }

    @Test
    void aGuardThatDoesNotHoldKeepsTheRegionWhereItIs() {
        Chart<Event, String> chart = Chart.<Event, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
                .on(Lamp.POWER, Power.OFF, Press.class).when((others, event) -> others.get(Lamp.MODE) == Mode.BRIGHT)
                .to(Power.ON)
                .on(Lamp.MODE, Mode.DIM, Tick.class).to(Mode.BRIGHT)
                .build();

        Step<String> step = chart.step(chart.initial(), new Press());

        assertThat(step.configuration().get(Lamp.POWER)).isEqualTo(Power.OFF);
    }

    @Test
    void aGuardCannotReadTheRegionItsEdgeLeaves() {
        Chart<Event, String> chart = Chart.<Event, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .on(Lamp.POWER, Power.OFF, Press.class).when((others, event) -> others.get(Lamp.POWER) == Power.OFF)
                .to(Power.ON)
                .build();
        Configuration start = chart.initial();

        assertThatThrownBy(() -> chart.step(start, new Press())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exitEffectsComeBeforeEdgeEffectsEachInDocumentOrder() {
        Chart<Event, String> chart = Chart.<Event, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
                .onExit(Lamp.POWER, Power.OFF, "left off")
                .onExit(Lamp.MODE, Mode.DIM, "left dim")
                .on(Lamp.POWER, Power.OFF, Press.class).emit("on").to(Power.ON)
                .on(Lamp.MODE, Mode.DIM, Press.class).emit("bright").to(Mode.BRIGHT)
                .build();

        Step<String> step = chart.step(chart.initial(), new Press());

        assertThat(step.effects()).containsExactly("left off", "left dim", "on", "bright");
    }

    @Test
    void parallelRegionsTakeTheSameEventAgainstTheConfigurationFromBeforeTheStep() {
        Chart<Event, String> chart = Chart.<Event, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
                .on(Lamp.POWER, Power.OFF, Press.class).to(Power.ON)
                .on(Lamp.MODE, Mode.DIM, Press.class).when((others, event) -> others.get(Lamp.POWER) == Power.OFF)
                .to(Mode.BRIGHT)
                .build();

        Configuration after = chart.step(chart.initial(), new Press()).configuration();

        assertThat(after.toPlain()).containsEntry("power", "ON").containsEntry("mode", "BRIGHT");
    }

    @Test
    void raisedEventsRunToCompletionInTheOrderTheyWereRaised() {
        Chart<Event, String> chart = Chart.<Event, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
                .on(Lamp.POWER, Power.OFF, Press.class).emit("on").raise(new Tick()).raise(new Hit()).to(Power.ON)
                .on(Lamp.STAGE, Stage.LOW, Tick.class).emit("tick").to(Stage.MID)
                .on(Lamp.STAGE, Stage.MID, Hit.class).emit("hit").to(Stage.HIGH)
                .build();

        Step<String> step = chart.step(chart.initial(), new Press());

        assertThat(step.configuration().get(Lamp.STAGE)).isEqualTo(Stage.HIGH);
        assertThat(step.effects()).containsExactly("on", "tick", "hit");
    }

    @Test
    void aCycleOfRaisedEventsStopsAtTheCeilingNamingTheCycle() {
        Chart<Event, String> chart = Chart.<Event, String>builder()
                .region(Lamp.POWER, Power.OFF)
                .on(Lamp.POWER, Power.OFF, Press.class).raise(new Press()).to(Power.ON)
                .on(Lamp.POWER, Power.ON, Press.class).raise(new Press()).to(Power.OFF)
                .raiseCeiling(5)
                .build();
        Configuration start = chart.initial();

        assertThatThrownBy(() -> chart.step(start, new Press())).isInstanceOf(RaiseCeilingExceeded.class)
                .hasMessageContaining("power OFF->ON").hasMessageContaining("power ON->OFF");
    }

    @Test
    void anEventNoEdgeTakesChangesNothing() {
        Chart<Event, String> chart = Chart.<Event, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .on(Lamp.POWER, Power.OFF, Press.class).emit("lit").to(Power.ON)
                .build();
        Configuration start = chart.initial();

        Step<String> step = chart.step(start, new Hit());

        assertThat(step.configuration()).isEqualTo(start);
        assertThat(step.effects()).isEmpty();
    }

    @Test
    void aDerivedRegionIsComputedFromTheEventAndNeverStored() {
        Chart<Event, String> chart = Chart.<Event, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .derived(Lamp.LEVEL, event -> event instanceof Press ? Mode.BRIGHT : Mode.DIM)
                .on(Lamp.POWER, Power.OFF, Event.class).when((others, event) -> others.get(Lamp.LEVEL) == Mode.BRIGHT)
                .to(Power.ON)
                .build();

        Step<String> pressed = chart.step(chart.initial(), new Press());
        Step<String> hit = chart.step(chart.initial(), new Hit());

        assertThat(chart.initial().toPlain()).containsOnlyKeys("power");
        assertThat(pressed.configuration().get(Lamp.POWER)).isEqualTo(Power.ON);
        assertThat(hit.configuration().get(Lamp.POWER)).isEqualTo(Power.OFF);
    }
}
