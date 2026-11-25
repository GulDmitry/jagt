package dev.jagt.orchestrator.service.report;

import dev.jagt.orchestrator.service.review.ReachOffers;
import lombok.With;

@With
public record TestReachOffersBuilder(boolean scenarios) {

  public static TestReachOffersBuilder reachOffers() {
    return new TestReachOffersBuilder(false);
  }

  public ReachOffers build() {
    return new ReachOffers(() -> scenarios, () -> false);
  }
}
