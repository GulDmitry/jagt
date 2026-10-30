package dev.jagt.statechart;

import dev.jagt.statechart.Lamp.Event;
import dev.jagt.statechart.Lamp.Facts;
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

class ChartTest {

    @Test
    void aStateNothingReachesIsReported() {
        ChartBuilder<Event, Facts, String> builder = Chart.<Event, Facts, String>builder()
                .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.MID, Stage.HIGH))
                .on(Lamp.STAGE, Stage.LOW, Press.class).to(Stage.MID);

        assertThatThrownBy(builder::build).isInstanceOfSatisfying(ChartInvalid.class,
                invalid -> assertThat(invalid.problems()).containsExactly("state stage.HIGH is unreachable"));
    }

    @Test
    void aNonFinalStateWithNoEdgeOutIsReported() {
        ChartBuilder<Event, Facts, String> builder = Chart.<Event, Facts, String>builder()
                .region(Lamp.POWER, Power.OFF)
                .on(Lamp.POWER, Power.OFF, Press.class).to(Power.ON);

        assertThatThrownBy(builder::build).isInstanceOfSatisfying(ChartInvalid.class,
                invalid -> assertThat(invalid.problems()).containsExactly("state power.ON has no way out"));
    }

    @Test
    void aRegionDeclaredTwiceIsReported() {
        ChartBuilder<Event, Facts, String> builder = Chart.<Event, Facts, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .on(Lamp.POWER, Power.OFF, Press.class).to(Power.ON);

        assertThatThrownBy(builder::build).isInstanceOfSatisfying(ChartInvalid.class,
                invalid -> assertThat(invalid.problems()).contains("region power is declared more than once"));
    }

    @Test
    void aEdgeOnADerivedRegionIsReported() {
        ChartBuilder<Event, Facts, String> builder = Chart.<Event, Facts, String>builder()
                .derived(Lamp.LEVEL, Facts::level)
                .on(Lamp.LEVEL, Mode.DIM, Press.class).to(Mode.BRIGHT);

        assertThatThrownBy(builder::build).isInstanceOfSatisfying(ChartInvalid.class,
                invalid -> assertThat(invalid.problems()).containsExactly("derived region level has a transition"));
    }

    @Test
    void everyProblemIsReportedAtOnce() {
        ChartBuilder<Event, Facts, String> builder = Chart.<Event, Facts, String>builder()
                .region(Lamp.STAGE, Stage.LOW, Set.of())
                .on(Lamp.STAGE, Stage.LOW, Press.class).to(Stage.MID);

        assertThatThrownBy(builder::build).isInstanceOfSatisfying(ChartInvalid.class,
                invalid -> assertThat(invalid.problems()).containsExactly("state stage.MID has no way out",
                        "state stage.HIGH has no way out", "state stage.HIGH is unreachable"));
    }

    @Test
    void aConfigurationRoundTripsThroughItsPlainData() {
        Chart<Event, Facts, String> chart = Chart.<Event, Facts, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
                .on(Lamp.POWER, Power.OFF, Press.class).to(Power.ON)
                .on(Lamp.MODE, Mode.DIM, Tick.class).to(Mode.BRIGHT)
                .build();
        Configuration moved = chart.step(chart.initial(), new Facts(Mode.DIM), new Tick()).configuration();

        Configuration restored = chart.configuration(moved.toPlain());

        assertThat(moved.toPlain()).containsEntry("power", "OFF").containsEntry("mode", "BRIGHT");
        assertThat(restored).isEqualTo(moved);
    }

    @Test
    void aConfigurationNamingAnUnknownStateIsRejected() {
        Chart<Event, Facts, String> chart = Chart.<Event, Facts, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .on(Lamp.POWER, Power.OFF, Press.class).to(Power.ON)
                .build();
        Map<String, String> plain = Map.of("power", "BROKEN");

        assertThatThrownBy(() -> chart.configuration(plain)).isInstanceOf(InvalidConfiguration.class);
    }

    @Test
    void aConfigurationNamingAnUnknownRegionIsRejected() {
        Chart<Event, Facts, String> chart = Chart.<Event, Facts, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .on(Lamp.POWER, Power.OFF, Press.class).to(Power.ON)
                .build();
        Map<String, String> plain = Map.of("power", "OFF", "mode", "DIM");

        assertThatThrownBy(() -> chart.configuration(plain)).isInstanceOf(InvalidConfiguration.class);
    }

    @Test
    void theRenderingIsAStableMermaidDiagramOfEveryRegion() {
        Chart<Event, Facts, String> chart = Chart.<Event, Facts, String>builder()
                .region(Lamp.POWER, Power.OFF, Set.of(Power.ON))
                .region(Lamp.MODE, Mode.DIM, Set.of(Mode.BRIGHT))
                .derived(Lamp.LEVEL, Facts::level)
                .on(Lamp.POWER, Power.OFF, Press.class).to(Power.ON)
                .on(Lamp.MODE, Mode.DIM, Tick.class).to(Mode.BRIGHT)
                .build();

        assertThat(chart.render()).isEqualTo("""
                stateDiagram-v2
                    state power {
                        state "OFF" as power_OFF
                        state "ON" as power_ON
                        [*] --> power_OFF
                        power_OFF --> power_ON : Press
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
}
