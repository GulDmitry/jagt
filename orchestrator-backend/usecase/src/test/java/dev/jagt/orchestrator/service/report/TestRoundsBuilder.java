package dev.jagt.orchestrator.service.report;

import static dev.jagt.orchestrator.service.report.TestCheckersBuilder.checkers;
import static dev.jagt.orchestrator.service.report.TestReachOffersBuilder.reachOffers;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.review.Rounds;
import lombok.With;

@With
public record TestRoundsBuilder() {

  public static TestRoundsBuilder rounds() {
    return new TestRoundsBuilder();
  }

  public Rounds build(ConfigService configService) {
    return new Rounds(
        configService, ignored -> false, reachOffers().build(), unheld -> null, checkers().build());
  }
}
