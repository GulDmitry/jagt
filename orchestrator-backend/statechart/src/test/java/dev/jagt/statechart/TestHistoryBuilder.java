package dev.jagt.statechart;

import dev.jagt.statechart.TestIrpBuilder.Ev;
import java.util.Set;

record TestHistoryBuilder() {

  enum Place {
    S0,
    S01,
    S011,
    S012,
    S02,
    S021,
    S022,
    S3,
    S4,
    PASS
  }

  enum Twin {
    S0,
    S01,
    S011,
    S02,
    S021,
    S022,
    S1,
    S11,
    S111,
    S12,
    S121,
    S122,
    S3,
    S4,
    PASS
  }

  static final RegionId<Place> PLACE = new RegionId<>("place", Place.class);

  static StatechartBuilder<Ev, String, String> skeleton() {
    return Statechart.<Ev, String, String>builder()
        .compound(PLACE, Place.S0, Place.S01, Place.S01, Place.S02)
        .compound(PLACE, Place.S01, Place.S011, Place.S011, Place.S012)
        .compound(PLACE, Place.S02, Place.S021, Place.S021, Place.S022)
        .raiseOnEntry(PLACE, Place.S011, new Ev("enteringS011"))
        .raiseOnEntry(PLACE, Place.S012, new Ev("enteringS012"))
        .raiseOnEntry(PLACE, Place.S021, new Ev("enteringS021"))
        .raiseOnEntry(PLACE, Place.S022, new Ev("enteringS022"));
  }

  static Statechart<Ev, String, String> deepThenShallow() {
    return skeleton()
        .region(PLACE, Place.S012, Set.of(Place.PASS))
        .history(PLACE, Place.S0, History.SHALLOW, Place.S02)
        .history(PLACE, Place.S0, History.DEEP, Place.S022)
        .onEntry(PLACE, Place.S0, "S0")
        .onEntry(PLACE, Place.S01, "S01")
        .onEntry(PLACE, Place.S011, "S011")
        .onEntry(PLACE, Place.S012, "S012")
        .onMatching(PLACE, Place.S0, "first", e -> e.name().equals("first"))
        .to(Place.S4)
        .always(PLACE, Place.S4)
        .toHistory(Place.S0, History.DEEP)
        .onMatching(PLACE, Place.S0, "enteringS012", e -> e.name().equals("enteringS012"))
        .to(Place.S3)
        .always(PLACE, Place.S3)
        .toHistory(Place.S0, History.SHALLOW)
        .onMatching(PLACE, Place.S0, "enteringS011", e -> e.name().equals("enteringS011"))
        .to(Place.PASS)
        .build();
  }
}
