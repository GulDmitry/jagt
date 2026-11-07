package dev.jagt.orchestrator.flow;

enum Fact {
  NO,
  YES;

  static Fact of(boolean holds) {
    return holds ? YES : NO;
  }
}
