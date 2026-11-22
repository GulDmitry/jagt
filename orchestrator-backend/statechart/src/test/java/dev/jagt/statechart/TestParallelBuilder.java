package dev.jagt.statechart;

import dev.jagt.statechart.TestIrpBuilder.Ev;
import java.util.Set;

record TestParallelBuilder() {

  enum Outer {
    START,
    WRAP,
    P,
    DONE
  }

  enum Branch {
    WHOLE,
    WORKING,
    FINISHED
  }

  static final RegionId<Outer> OUTER = new RegionId<>("outer", Outer.class);

  static final RegionId<Branch> LEFT = new RegionId<>("left", Branch.class);

  static final RegionId<Branch> RIGHT = new RegionId<>("right", Branch.class);

  static StatechartBuilder<Ev, String, String> skeleton() {
    return skeleton(Outer.START, Branch.WHOLE);
  }

  static StatechartBuilder<Ev, String, String> skeleton(Outer initial, Branch leftInitial) {
    return Statechart.<Ev, String, String>builder()
        .region(OUTER, initial, Set.of(Outer.DONE))
        .region(LEFT, leftInitial, Set.of(Branch.FINISHED))
        .region(RIGHT, Branch.WHOLE, Set.of(Branch.FINISHED))
        .compound(OUTER, Outer.WRAP, Outer.P, Outer.P)
        .compound(LEFT, Branch.WHOLE, Branch.WORKING, Branch.WORKING, Branch.FINISHED)
        .compound(RIGHT, Branch.WHOLE, Branch.WORKING, Branch.WORKING, Branch.FINISHED)
        .parallel(OUTER, Outer.P, LEFT, RIGHT)
        .onMatching(OUTER, Outer.START, "go", e -> e.name().equals("go"))
        .to(Outer.WRAP)
        .onMatching(OUTER, Outer.P, "leave", e -> e.name().equals("leave"))
        .to(Outer.DONE)
        .onMatching(OUTER, Outer.DONE, "restart", e -> e.name().equals("restart"))
        .to(Outer.START)
        .onMatching(LEFT, Branch.WORKING, "finishLeft", e -> e.name().equals("finishLeft"))
        .to(Branch.FINISHED)
        .onMatching(LEFT, Branch.FINISHED, "again", e -> e.name().equals("again"))
        .to(Branch.WORKING)
        .onMatching(RIGHT, Branch.WORKING, "finishRight", e -> e.name().equals("finishRight"))
        .to(Branch.FINISHED)
        .onMatching(RIGHT, Branch.FINISHED, "again", e -> e.name().equals("again"))
        .to(Branch.WORKING);
  }
}
