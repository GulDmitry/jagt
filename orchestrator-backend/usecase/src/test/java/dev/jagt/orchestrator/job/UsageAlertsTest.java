package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.SubscriptionUsage.Paid;
import dev.jagt.orchestrator.port.SubscriptionUsage.Snapshot;
import dev.jagt.orchestrator.port.SubscriptionUsage.Window;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class UsageAlertsTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final Optional<Instant> RESET = Optional.of(Instant.parse("2026-10-10T16:00:00Z"));

    @Test
    void tellsOnlyTheHighestThresholdAWindowJumpedPast() {
        var alerts = new UsageAlerts(List.of(80, 95), List.of(), ZoneOffset.UTC);

        var told = alerts.after(new Snapshot(List.of(new Window("5h", 96, RESET)), Optional.empty()), NOW);

        assertThat(told).extracting(Notification::title).containsExactly("5h window at 95%");
    }

    @Test
    void tellsAThresholdOncePerWindowPeriod() {
        var alerts = new UsageAlerts(List.of(80), List.of(), ZoneOffset.UTC);
        alerts.after(new Snapshot(List.of(new Window("week", 81, RESET)), Optional.empty()), NOW);

        var told = alerts.after(new Snapshot(List.of(new Window("week", 84, RESET)), Optional.empty()), NOW);

        assertThat(told).isEmpty();
    }

    @Test
    void tellsAThresholdOnceWhenTheSameResetIsReportedWithDifferentFractions() {
        var alerts = new UsageAlerts(List.of(80), List.of(), ZoneOffset.UTC);
        alerts.after(new Snapshot(List.of(new Window("5h", 81,
                Optional.of(Instant.parse("2026-10-10T16:00:00.574417Z")))), Optional.empty()), NOW);

        var told = alerts.after(new Snapshot(List.of(new Window("5h", 82,
                Optional.of(Instant.parse("2026-10-10T15:59:59.912003Z")))), Optional.empty()), NOW);

        assertThat(told).isEmpty();
    }

    @Test
    void tellsTheThresholdAgainInTheNextPeriod() {
        var alerts = new UsageAlerts(List.of(80), List.of(), ZoneOffset.UTC);
        alerts.after(new Snapshot(List.of(new Window("5h", 81, RESET)), Optional.empty()), NOW);

        var told = alerts.after(new Snapshot(List.of(new Window("5h", 85,
                Optional.of(Instant.parse("2026-10-10T21:00:00Z")))), Optional.empty()), NOW);

        assertThat(told).extracting(Notification::title).containsExactly("5h window at 80%");
    }

    @Test
    void tellsPaidUsageStartedWhenAWindowRunsOutWithPaidUsageEnabled() {
        var alerts = new UsageAlerts(List.of(), List.of(), ZoneOffset.UTC);
        var paid = new Paid(Optional.of(BigDecimal.ZERO), Optional.of(new BigDecimal("500")), "USD", Optional.empty());

        var told = alerts.after(new Snapshot(List.of(new Window("5h", 100, RESET)), Optional.of(paid)), NOW);

        assertThat(told).extracting(Notification::title, Notification::body)
                .containsExactly(tuple("paid usage started", "0.00 of 500.00 USD"));
    }

    @Test
    void tellsPaidUsageStartedWhenItsSpendRisesWithNoWindowRunOut() {
        var alerts = new UsageAlerts(List.of(), List.of(), ZoneOffset.UTC);
        alerts.after(new Snapshot(List.of(new Window("week", 40, RESET)), Optional.of(
                new Paid(Optional.of(new BigDecimal("3")), Optional.of(new BigDecimal("500")), "USD", Optional.empty()))), NOW);

        var told = alerts.after(new Snapshot(List.of(new Window("week", 40, RESET)), Optional.of(
                new Paid(Optional.of(new BigDecimal("4")), Optional.of(new BigDecimal("500")), "USD", Optional.empty()))), NOW);

        assertThat(told).extracting(Notification::title).containsExactly("paid usage started");
    }

    @Test
    void tellsPaidUsageStartedOnceWhileTheSameWindowStaysRunOut() {
        var alerts = new UsageAlerts(List.of(), List.of(), ZoneOffset.UTC);
        alerts.after(new Snapshot(List.of(new Window("5h", 100, RESET)), Optional.of(
                new Paid(Optional.of(new BigDecimal("3")), Optional.of(new BigDecimal("500")), "USD", Optional.empty()))), NOW);

        var told = alerts.after(new Snapshot(List.of(new Window("5h", 100, RESET)), Optional.of(
                new Paid(Optional.of(new BigDecimal("9")), Optional.of(new BigDecimal("500")), "USD", Optional.empty()))), NOW);

        assertThat(told).isEmpty();
    }

    @Test
    void tellsPaidUsageCrossingAThresholdOfItsMonthlyLimit() {
        var alerts = new UsageAlerts(List.of(), List.of(80), ZoneOffset.UTC);

        var told = alerts.after(new Snapshot(List.of(new Window("week", 40, RESET)), Optional.of(
                new Paid(Optional.of(new BigDecimal("410")), Optional.of(new BigDecimal("500")), "USD", Optional.empty()))), NOW);

        assertThat(told).extracting(Notification::title).containsExactly("paid usage at 80% of its limit");
    }
}
