package dev.jagt.statechart;

import java.util.List;

record Traversal(List<Enum<?>> exits, List<Entry> entries, Enum<?> leaf) {

  record Entry(Enum<?> state, boolean byDefault) {}

  static Traversal none(Enum<?> leaf) {
    return new Traversal(List.of(), List.of(), leaf);
  }
}
