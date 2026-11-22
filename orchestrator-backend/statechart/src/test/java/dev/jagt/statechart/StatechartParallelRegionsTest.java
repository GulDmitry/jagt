package dev.jagt.statechart;

import static dev.jagt.statechart.TestParallelBuilder.LEFT;
import static dev.jagt.statechart.TestParallelBuilder.OUTER;
import static dev.jagt.statechart.TestParallelBuilder.RIGHT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.statechart.TestIrpBuilder.Ev;
import dev.jagt.statechart.TestParallelBuilder.Branch;
import dev.jagt.statechart.TestParallelBuilder.Outer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatechartParallelRegionsTest {

  private static final Map<String, String> INSIDE =
      Map.of("outer", "P", "left", "WORKING", "right", "WORKING");

  @Test
  void historyOfCompoundInsideRegionRecordsWhereTheParallelStateWasLeft() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton()
            .history(LEFT, Branch.WHOLE, History.SHALLOW, Branch.WORKING)
            .build();

    Macrostep<String> step = chart.step(chart.configuration(INSIDE), "", new Ev("leave"));

    assertThat(step.configuration().history()).isEqualTo(Map.of("left.WHOLE.SHALLOW", "WORKING"));
  }

  @Test
  void parallelStateAlreadyDoneIsNotReportedDoneAgainByLaterMicrostep() {
    Statechart<Ev, String, String> chart =
        TestParallelBuilder.skeleton()
            .completion(done -> new Ev(done instanceof Completed.OfState s ? s.state() : "chart"))
            .onMatching(OUTER, Outer.P, "ping", e -> e.name().equals("ping"))
            .emit("pinged")
            .internal()
            .onMatching(OUTER, Outer.P, "done", e -> e.name().equals("P"))
            .emit("done P")
            .internal()
            .build();
    Configuration alreadyDone =
        chart.configuration(Map.of("outer", "P", "left", "FINISHED", "right", "FINISHED"));

    Macrostep<String> step = chart.step(alreadyDone, "", new Ev("ping"));

    assertThat(step.effects()).containsExactly("pinged");
  }

  @Test
  void transitionOfAnotherRegionIsNotCountedForTheRegionAsked() {
    Statechart<Ev, String, String> chart = TestParallelBuilder.skeleton().build();
    Configuration inside = chart.configuration(INSIDE);

    boolean rightTakes = chart.takes(RIGHT, inside, "", new Ev("finishLeft"));

    assertThat(rightTakes).isFalse();
  }
}
