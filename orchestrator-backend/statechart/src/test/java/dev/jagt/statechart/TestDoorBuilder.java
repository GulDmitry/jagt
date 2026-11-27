package dev.jagt.statechart;

import java.util.Set;

record TestDoorBuilder() {

  enum Door {
    SHUT,
    OPEN
  }

  enum Bell {
    QUIET,
    RINGING
  }

  enum Hall {
    LOBBY,
    STAIRS,
    FIRST,
    SECOND
  }

  sealed interface Nudge {

    record Shove() implements Nudge {}

    record Tug() implements Nudge {}
  }

  static final RegionId<Door> DOOR = new RegionId<>("door", Door.class);
  static final RegionId<Hall> HALL = new RegionId<>("hall", Hall.class);
  static final RegionId<Bell> BELL = new RegionId<>("bell", Bell.class);

  static StatechartBuilder<Nudge, String, String> shovedBothWays() {
    StatechartBuilder<Nudge, String, String> builder =
        Statechart.<Nudge, String, String>builder()
            .region(DOOR, Door.SHUT)
            .moves(Nudge.class)
            .addressed(Nudge.Shove.class, DOOR)
            .addressed(Nudge.Tug.class, DOOR);
    builder.onMatching(DOOR, Door.SHUT, Nudge.Shove.class, "shove", nudge -> true).to(Door.OPEN);
    builder.onMatching(DOOR, Door.OPEN, Nudge.Shove.class, "shove", nudge -> true).to(Door.SHUT);
    return builder;
  }

  static StatechartBuilder<Nudge, String, String> tuggedOnlyWhenShut() {
    StatechartBuilder<Nudge, String, String> builder = shovedBothWays();
    builder.onMatching(DOOR, Door.SHUT, Nudge.Tug.class, "tug", nudge -> true).to(Door.OPEN);
    builder.ignores(DOOR, Nudge.Tug.class, Set.of(Door.OPEN), "an open door has nothing to pull");
    return builder;
  }
}
