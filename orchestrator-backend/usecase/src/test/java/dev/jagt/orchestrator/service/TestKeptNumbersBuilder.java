package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestReachLedgerBuilder.reachLedger;

import lombok.With;

@With
public record TestKeptNumbersBuilder() {

  public static TestKeptNumbersBuilder keptNumbers() {
    return new TestKeptNumbersBuilder();
  }

  public KeptNumbers build() {
    return new KeptNumbers(reachLedger().build());
  }
}
