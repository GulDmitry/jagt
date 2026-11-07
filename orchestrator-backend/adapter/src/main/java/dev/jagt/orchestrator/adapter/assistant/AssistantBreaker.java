package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.AssistantUnavailable;
import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.port.ReachLost;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stops headless reads after consecutive provider failures, then lets one probe through once the
 * cool-down has passed. A refused read is never queued.
 */
@Component
@Slf4j
class AssistantBreaker {

  private static final class ProviderFailure extends RuntimeException {

    ProviderFailure(String why) {
      super(why, null, false, false);
    }
  }

  private static final String DRIVER = "assistant";
  private static final String HOST = "local";

  private final CircuitBreaker breaker;
  private final Clock clock;
  private final Reach reach;
  private final int threshold;
  private final long cooldownMillis;
  private volatile String cause = "";

  AssistantBreaker(AssistantProperties assistant, Clock clock, Reach reach) {
    this.clock = clock;
    this.reach = reach;
    this.threshold = assistant.breakerFailures();
    this.cooldownMillis = assistant.breakerCooldown().toMillis();
    this.breaker =
        CircuitBreaker.of(
            "assistant",
            CircuitBreakerConfig.custom()
                .clock(clock)
                .slidingWindow(threshold, threshold, SlidingWindowType.COUNT_BASED)
                .failureRateThreshold(100)
                .waitDurationInOpenState(assistant.breakerCooldown())
                .permittedNumberOfCallsInHalfOpenState(1)
                .build());
    breaker
        .getEventPublisher()
        .onStateTransition(
            event ->
                transitioned(
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));
  }

  void admit() {
    if (!breaker.tryAcquirePermission()) {
      throw new AssistantUnavailable(cause);
    }
  }

  void succeeded() {
    breaker.onSuccess(0, TimeUnit.NANOSECONDS);
  }

  void failed(String why) {
    cause = why;
    breaker.onError(0, TimeUnit.NANOSECONDS, new ProviderFailure(why));
  }

  /**
   * An outcome that says nothing about the provider frees a probe slot and leaves the streak alone.
   */
  void inconclusive() {
    breaker.releasePermission();
  }

  private void transitioned(CircuitBreaker.State from, CircuitBreaker.State to) {
    switch (to) {
      case OPEN -> opened(from);
      case HALF_OPEN ->
          log.atInfo().setMessage("assistant probe started").addKeyValue("cause", cause).log();
      case CLOSED -> closed(from);
      default -> {}
    }
  }

  private void opened(CircuitBreaker.State from) {
    reach.lost(
        new ReachLost(
            DRIVER,
            HOST,
            List.of(),
            "assistant unavailable: " + cause,
            clock.instant().toEpochMilli() + cooldownMillis),
        () -> breaker.getState() != CircuitBreaker.State.CLOSED);
    log.atWarn()
        .setMessage(
            from == CircuitBreaker.State.HALF_OPEN
                ? "assistant breaker reopened"
                : "assistant breaker opened")
        .addKeyValue("cause", cause)
        .addKeyValue("limit", threshold)
        .addKeyValue("effect", "headless reads refused until the next probe")
        .addKeyValue("fix", "orchestrator.assistant.breakerCooldown")
        .log();
  }

  private void closed(CircuitBreaker.State from) {
    log.atInfo()
        .setMessage("assistant breaker closed")
        .addKeyValue("previous", from.name().toLowerCase(Locale.ROOT))
        .addKeyValue("cause", cause)
        .log();
    reach.reached(DRIVER, HOST);
    cause = "";
  }
}
