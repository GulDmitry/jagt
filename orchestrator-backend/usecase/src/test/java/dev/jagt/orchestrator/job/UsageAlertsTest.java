package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.SubscriptionUsage.Paid;
import dev.jagt.orchestrator.port.SubscriptionUsage.Snapshot;
import dev.jagt.orchestrator.port.SubscriptionUsage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class UsageAlertsTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final Optional<Instant> RESET = Optional.of(Instant.parse("2026-10-10T16:00:00Z"));

    @Test
    void tellsOnlyTheHighestThresholdAWindowJumpedPast(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(80, 95), List.of(), dir.resolve("usage-alerts"));

        var told = alerts.after(new Snapshot(List.of(new Window("5h", 96, RESET)), Optional.empty()), NOW);

        assertThat(told).extracting(Notification::title).containsExactly("5h window at 95%");
    }

    @Test
    void tellsAThresholdOncePerWindowPeriodAcrossARestart(@TempDir Path dir) {
        new UsageAlerts(List.of(80), List.of(), dir.resolve("usage-alerts"))
                .after(new Snapshot(List.of(new Window("week", 81, RESET)), Optional.empty()), NOW);

        var told = new UsageAlerts(List.of(80), List.of(), dir.resolve("usage-alerts"))
                .after(new Snapshot(List.of(new Window("week", 84, RESET)), Optional.empty()), NOW);

        assertThat(told).isEmpty();
    }

    @Test
    void tellsAThresholdOncePerWindowPeriod(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(80), List.of(), dir.resolve("usage-alerts"));
        alerts.after(new Snapshot(List.of(new Window("week", 81, RESET)), Optional.empty()), NOW);

        var told = alerts.after(new Snapshot(List.of(new Window("week", 84, RESET)), Optional.empty()), NOW);

        assertThat(told).isEmpty();
    }

    @Test
    void tellsAThresholdOnceWhenTheSameResetIsReportedWithDifferentFractions(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(80), List.of(), dir.resolve("usage-alerts"));
        alerts.after(new Snapshot(List.of(new Window("5h", 81,
                Optional.of(Instant.parse("2026-10-10T16:00:00.574417Z")))), Optional.empty()), NOW);

        var told = alerts.after(new Snapshot(List.of(new Window("5h", 82,
                Optional.of(Instant.parse("2026-10-10T15:59:59.912003Z")))), Optional.empty()), NOW);

        assertThat(told).isEmpty();
    }

    @Test
    void tellsTheThresholdAgainInTheNextPeriod(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(80), List.of(), dir.resolve("usage-alerts"));
        alerts.after(new Snapshot(List.of(new Window("5h", 81, RESET)), Optional.empty()), NOW);

        var told = alerts.after(new Snapshot(List.of(new Window("5h", 85,
                Optional.of(Instant.parse("2026-10-10T21:00:00Z")))), Optional.empty()), NOW);

        assertThat(told).extracting(Notification::title).containsExactly("5h window at 80%");
    }

    @Test
    void tellsPaidUsageStartedWhenAWindowRunsOutWithPaidUsageEnabled(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(), List.of(), dir.resolve("usage-alerts"));
        var paid = new Paid(Optional.of(BigDecimal.ZERO), Optional.of(new BigDecimal("500")), "USD", Optional.empty());

        var told = alerts.after(new Snapshot(List.of(new Window("5h", 100, RESET)), Optional.of(paid)), NOW);

        assertThat(told).extracting(Notification::title, Notification::body)
                .containsExactly(tuple("paid usage started", "0.00 of 500.00 USD"));
    }

    @Test
    void tellsPaidUsageStartedWhenItsSpendRisesWithNoWindowRunOut(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(), List.of(), dir.resolve("usage-alerts"));
        alerts.after(new Snapshot(List.of(new Window("week", 40, RESET)), Optional.of(
                new Paid(Optional.of(new BigDecimal("3")), Optional.of(new BigDecimal("500")), "USD", Optional.empty()))), NOW);

        var told = alerts.after(new Snapshot(List.of(new Window("week", 40, RESET)), Optional.of(
                new Paid(Optional.of(new BigDecimal("4")), Optional.of(new BigDecimal("500")), "USD", Optional.empty()))), NOW);

        assertThat(told).extracting(Notification::title).containsExactly("paid usage started");
    }

    @Test
    void tellsPaidUsageStartedOnceWhileTheSameWindowStaysRunOut(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(), List.of(), dir.resolve("usage-alerts"));
        alerts.after(new Snapshot(List.of(new Window("5h", 100, RESET)), Optional.of(
                new Paid(Optional.of(new BigDecimal("3")), Optional.of(new BigDecimal("500")), "USD", Optional.empty()))), NOW);

        var told = alerts.after(new Snapshot(List.of(new Window("5h", 100, RESET)), Optional.of(
                new Paid(Optional.of(new BigDecimal("9")), Optional.of(new BigDecimal("500")), "USD", Optional.empty()))), NOW);

        assertThat(told).isEmpty();
    }

    @Test
    void tellsPaidUsageCrossingAThresholdOfItsMonthlyLimit(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(), List.of(80), dir.resolve("usage-alerts"));

        var told = alerts.after(new Snapshot(List.of(new Window("week", 40, RESET)), Optional.of(
                new Paid(Optional.of(new BigDecimal("410")), Optional.of(new BigDecimal("500")), "USD", Optional.empty()))), NOW);

        assertThat(told).extracting(Notification::title).containsExactly("paid usage at 80% of its limit");
    }

    @Test
    void tellsAThresholdTheWindowReadsExactly(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(80), List.of(), dir.resolve("usage-alerts"));

        var told = alerts.after(new Snapshot(List.of(new Window("5h", 80, RESET)), Optional.empty()), NOW);

        assertThat(told).extracting(Notification::title).containsExactly("5h window at 80%");
    }

    @Test
    void tellsWhenAWindowResetsAndHowToReadItsBody(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(80), List.of(), dir.resolve("usage-alerts"));

        var told = alerts.after(new Snapshot(List.of(new Window("5h", 81, RESET),
                new Window("week", 81, Optional.empty())), Optional.empty()), NOW);

        assertThat(told).extracting(Notification::body).containsExactly("resets Sat 16:00", "no reset reported");
    }

    @Test
    void tellsPaidThresholdAgainAfterTheSpendReadsBelowItAndRisesBack(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(), List.of(80), dir.resolve("usage-alerts"));
        alerts.after(new Snapshot(List.of(), Optional.of(
                new Paid(Optional.of(new BigDecimal("410")), Optional.of(new BigDecimal("500")), "USD", Optional.empty()))), NOW);
        alerts.after(new Snapshot(List.of(), Optional.of(
                new Paid(Optional.of(new BigDecimal("100")), Optional.of(new BigDecimal("500")), "USD", Optional.empty()))), NOW);

        var told = alerts.after(new Snapshot(List.of(), Optional.of(
                new Paid(Optional.of(new BigDecimal("420")), Optional.of(new BigDecimal("500")), "USD", Optional.empty()))), NOW);

        assertThat(told).extracting(Notification::title).contains("paid usage at 80% of its limit");
    }

    @Test
    void tellsPaidUsageStartedWhenItsBalanceFalls(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(), List.of(), dir.resolve("usage-alerts"));
        alerts.after(new Snapshot(List.of(), Optional.of(
                new Paid(Optional.empty(), Optional.empty(), "USD", Optional.of(new BigDecimal("50"))))), NOW);

        var told = alerts.after(new Snapshot(List.of(), Optional.of(
                new Paid(Optional.empty(), Optional.empty(), "USD", Optional.of(new BigDecimal("49"))))), NOW);

        assertThat(told).extracting(Notification::title).containsExactly("paid usage started");
    }

    @Test
    void staysSilentWhenPaidSpendAndBalanceAreUnchanged(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(), List.of(), dir.resolve("usage-alerts"));
        var paid = new Paid(Optional.of(new BigDecimal("3")), Optional.of(new BigDecimal("500")), "USD",
                Optional.of(new BigDecimal("50")));
        alerts.after(new Snapshot(List.of(), Optional.of(paid)), NOW);

        var told = alerts.after(new Snapshot(List.of(), Optional.of(paid)), NOW);

        assertThat(told).isEmpty();
    }

    @Test
    void tellsPaidUsageStartedAgainWhenTheNextWindowRunsOut(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(), List.of(), dir.resolve("usage-alerts"));
        var paid = new Paid(Optional.of(new BigDecimal("3")), Optional.of(new BigDecimal("500")), "USD", Optional.empty());
        alerts.after(new Snapshot(List.of(new Window("5h", 100, RESET)), Optional.of(paid)), NOW);

        var told = alerts.after(new Snapshot(List.of(new Window("5h", 100,
                Optional.of(Instant.parse("2026-10-10T21:00:00Z")))), Optional.of(paid)), NOW);

        assertThat(told).extracting(Notification::title).containsExactly("paid usage started");
    }

    @Test
    void staysSilentOnPaidThresholdsWhenTheMonthlyLimitIsZero(@TempDir Path dir) {
        var alerts = new UsageAlerts(List.of(), List.of(80), dir.resolve("usage-alerts"));

        var told = alerts.after(new Snapshot(List.of(), Optional.of(
                new Paid(Optional.of(new BigDecimal("5")), Optional.of(BigDecimal.ZERO), "USD", Optional.empty()))), NOW);

        assertThat(told).isEmpty();
    }
}
