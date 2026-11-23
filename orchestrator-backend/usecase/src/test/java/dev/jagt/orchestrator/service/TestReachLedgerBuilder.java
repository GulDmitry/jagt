package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.service.reach.ReachLedger;
import lombok.With;

@With
public record TestReachLedgerBuilder() {

  public static TestReachLedgerBuilder reachLedger() {
    return new TestReachLedgerBuilder();
  }

  public ReachLedger build() {
    return new ReachLedger();
  }
}
