package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.UsageLimitsProperties;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.SubscriptionUsage;
import dev.jagt.orchestrator.service.UsageLimits;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/** Tells, never gates: nothing here refuses or slows a task. */
@Service
@ConditionalOnProperty(name = "orchestrator.usage-limits.enabled", havingValue = "true", matchIfMissing = true)
public class UsageLimitsJob implements Job {

    private final UsageLimits limits;
    private final Notifications notifications;
    private final Duration every;
    private final UsageAlerts alerts;
    private final Clock clock;

    public UsageLimitsJob(UsageLimits limits, Notifications notifications, UsageLimitsProperties properties,
                          Clock clock) {
        this.limits = limits;
        this.notifications = notifications;
        this.every = properties.every();
        this.alerts = new UsageAlerts(properties.thresholds(), properties.paidThresholds());
        this.clock = clock;
    }

    @Override
    public String id() {
        return "usage";
    }

    @Override
    public String describe() {
        return "read the subscription's limits and say when one fills or paid usage starts";
    }

    @Override
    public Duration every() {
        return every;
    }

    @Override
    public void run() {
        limits.refresh(clock.instant()).ifPresent(reading -> {
            if (reading.answer() instanceof SubscriptionUsage.Read(SubscriptionUsage.Snapshot snapshot)) {
                alerts.after(snapshot, reading.at()).forEach(notifications::send);
            }
        });
    }
}
