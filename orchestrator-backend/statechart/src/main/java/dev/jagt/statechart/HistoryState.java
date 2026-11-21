package dev.jagt.statechart;

record HistoryState(Enum<?> compound, History kind, Enum<?> target) {

  String key(String region) {
    return region + "." + compound.name() + "." + kind.name();
  }
}
