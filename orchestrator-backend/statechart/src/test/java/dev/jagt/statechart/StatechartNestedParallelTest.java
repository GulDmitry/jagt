package dev.jagt.statechart;

import static dev.jagt.statechart.TestNestedBuilder.CELL_C;
import static dev.jagt.statechart.TestNestedBuilder.CELL_D;
import static dev.jagt.statechart.TestNestedBuilder.LANE_A;
import static dev.jagt.statechart.TestNestedBuilder.LANE_B;
import static dev.jagt.statechart.TestNestedBuilder.OUTER;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.statechart.TestIrpBuilder.Ev;
import dev.jagt.statechart.TestNestedBuilder.Cell;
import dev.jagt.statechart.TestNestedBuilder.LaneA;
import dev.jagt.statechart.TestNestedBuilder.LaneB;
import dev.jagt.statechart.TestNestedBuilder.Top;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatechartNestedParallelTest {

  private static final Map<String, String> INSIDE =
      Map.of(
          "outer", "P", "laneA", "INNER", "laneB", "WHOLE", "cellC", "WORKING", "cellD", "WORKING");

  @Test
  void leavingTheOuterParallelStateExitsEveryNestedRegionLastFirst() {
    Statechart<Ev, String, String> chart =
        TestNestedBuilder.skeleton()
            .onExit(OUTER, Top.P, "exit P")
            .onExit(LANE_A, LaneA.INNER, "exit inner")
            .onExit(LANE_B, LaneB.WHOLE, "exit laneB")
            .onExit(CELL_C, Cell.WORKING, "exit C")
            .onExit(CELL_D, Cell.WORKING, "exit D")
            .build();

    Macrostep<String> step = chart.step(chart.configuration(INSIDE), "", new Ev("leave"));

    assertThat(step.effects())
        .containsExactly("exit laneB", "exit D", "exit C", "exit inner", "exit P");
    assertThat(step.configuration().toPlain()).isEqualTo(Map.of("outer", "DONE"));
  }

  @Test
  void nestedRegionsTransitionPreemptsTheOuterParallelStatesOwn() {
    Statechart<Ev, String, String> chart =
        TestNestedBuilder.skeleton()
            .onMatching(OUTER, Top.P, "stop", e -> e.name().equals("stop"))
            .emit("outer")
            .to(Top.DONE)
            .onMatching(CELL_C, Cell.WORKING, "stop", e -> e.name().equals("stop"))
            .emit("cell")
            .internal()
            .build();

    Macrostep<String> step = chart.step(chart.configuration(INSIDE), "", new Ev("stop"));

    assertThat(step.effects()).containsExactly("cell");
    assertThat(step.configuration().toPlain()).isEqualTo(INSIDE);
  }
}
