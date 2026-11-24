package dev.jagt.orchestrator.service.review;

import dev.jagt.orchestrator.flow.capability.Facts;

/** What the configuration lets jagt offer on any card. */
@FunctionalInterface
public interface ReachOffers {

  Facts.Reach facts();
}
