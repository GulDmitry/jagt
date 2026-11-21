package dev.jagt.statechart;

import dev.jagt.statechart.TestIrpBuilder.Ev;
import java.util.Set;

record TestNestedBuilder() {

  enum Top {
    START,
    P,
    DONE
  }

  enum LaneA {
    INNER,
    FINISHED
  }

  enum LaneB {
    WHOLE,
    FINISHED
  }

  enum Cell {
    WORKING,
    FINISHED
  }

  static final RegionId<Top> OUTER = new RegionId<>("outer", Top.class);

  static final RegionId<LaneA> LANE_A = new RegionId<>("laneA", LaneA.class);

  static final RegionId<LaneB> LANE_B = new RegionId<>("laneB", LaneB.class);

  static final RegionId<Cell> CELL_C = new RegionId<>("cellC", Cell.class);

  static final RegionId<Cell> CELL_D = new RegionId<>("cellD", Cell.class);

  static StatechartBuilder<Ev, String, String> skeleton() {
    return Statechart.<Ev, String, String>builder()
        .region(OUTER, Top.START, Set.of(Top.DONE))
        .region(LANE_A, LaneA.INNER, Set.of(LaneA.FINISHED))
        .region(LANE_B, LaneB.WHOLE, Set.of(LaneB.FINISHED))
        .region(CELL_C, Cell.WORKING, Set.of(Cell.FINISHED))
        .region(CELL_D, Cell.WORKING, Set.of(Cell.FINISHED))
        .parallel(OUTER, Top.P, LANE_A, LANE_B)
        .parallel(LANE_A, LaneA.INNER, CELL_C, CELL_D)
        .onMatching(OUTER, Top.START, "go", e -> e.name().equals("go"))
        .to(Top.P)
        .onMatching(OUTER, Top.P, "leave", e -> e.name().equals("leave"))
        .to(Top.DONE)
        .onMatching(LANE_A, LaneA.INNER, "out", e -> e.name().equals("out"))
        .to(LaneA.FINISHED)
        .onMatching(LANE_B, LaneB.WHOLE, "end", e -> e.name().equals("end"))
        .to(LaneB.FINISHED)
        .onMatching(CELL_C, Cell.WORKING, "finishC", e -> e.name().equals("finishC"))
        .to(Cell.FINISHED)
        .onMatching(CELL_D, Cell.WORKING, "finishD", e -> e.name().equals("finishD"))
        .to(Cell.FINISHED);
  }
}
