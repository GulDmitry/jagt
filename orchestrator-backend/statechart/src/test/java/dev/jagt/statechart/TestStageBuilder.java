package dev.jagt.statechart;

import dev.jagt.statechart.Lamp.Event;
import dev.jagt.statechart.Lamp.Press;
import dev.jagt.statechart.Lamp.Readings;
import dev.jagt.statechart.Lamp.Stage;
import java.util.Set;

record TestStageBuilder() {

  static Statechart<Event, Readings, String> compoundLow() {
    return Statechart.<Event, Readings, String>builder()
        .region(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH))
        .compound(Lamp.STAGE, Stage.LOW, Set.of(Stage.HIGH, Stage.MID))
        .on(Lamp.STAGE, Stage.MID, Press.class)
        .to(Stage.HIGH)
        .build();
  }
}
