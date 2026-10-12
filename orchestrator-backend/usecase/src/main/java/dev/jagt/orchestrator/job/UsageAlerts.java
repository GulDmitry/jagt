package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.SubscriptionUsage.Paid;
import dev.jagt.orchestrator.port.SubscriptionUsage.Snapshot;
import dev.jagt.orchestrator.port.SubscriptionUsage.Window;
import dev.jagt.orchestrator.service.UsageLimits;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Each event once per period: a window's period ends at its reset, and a level carrying no reset re-arms once
 * it reads below its threshold again.
 */
class UsageAlerts {

    private static final String NO_PERIOD = "-";
    private static final String PAID_START = "paid-start";
    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ROOT);

    private final List<Integer> thresholds;
    private final List<Integer> paidThresholds;
    private final ZoneId zone;
    private final Map<String, String> firedIn = new HashMap<>();
    private @Nullable Paid previous;

    UsageAlerts(List<Integer> thresholds, List<Integer> paidThresholds, ZoneId zone) {
        this.thresholds = thresholds.stream().sorted(Comparator.reverseOrder()).toList();
        this.paidThresholds = paidThresholds.stream().sorted(Comparator.reverseOrder()).toList();
        this.zone = zone;
    }

    List<Notification> after(Snapshot snapshot, Instant now) {
        List<Notification> due = new ArrayList<>();
        for (Window window : snapshot.windows()) {
            String period = periodOf(window.resetsAt());
            crossed("window|" + window.name(), window.percentUsed(), thresholds, period).ifPresent(level ->
                    due.add(Notification.usage(window.name() + " window at " + level + "%",
                            window.resetsAt().map(at -> "resets " + AT.format(at.atZone(zone)))
                                    .orElse("no reset reported"))));
        }
        snapshot.paid().ifPresent(paid -> {
            spendingSince(snapshot, paid, now).ifPresent(period -> {
                if (!period.equals(firedIn.put(PAID_START, period))) {
                    due.add(Notification.usage("paid usage started", UsageLimits.spent(paid)));
                }
            });
            percentOfLimit(paid).flatMap(percent -> crossed("paid", percent, paidThresholds, NO_PERIOD))
                    .ifPresent(level -> due.add(Notification.usage("paid usage at " + level + "% of its limit",
                            UsageLimits.spent(paid))));
        });
        previous = snapshot.paid().orElse(null);
        return due;
    }

    /** The highest threshold first reached in {@code period}, every lower one counted as reached with it. */
    private Optional<Integer> crossed(String subject, double percent, List<Integer> levels, String period) {
        Optional<Integer> highest = Optional.empty();
        for (int level : levels) {
            String key = subject + "|" + level;
            if (percent < level) {
                if (NO_PERIOD.equals(period)) {
                    firedIn.remove(key);
                }
            } else if (!period.equals(firedIn.put(key, period)) && highest.isEmpty()) {
                highest = Optional.of(level);
            }
        }
        return highest;
    }

    /** The period money is flowing in: until the last exhausted window resets, else this calendar month. */
    private Optional<String> spendingSince(Snapshot snapshot, Paid paid, Instant now) {
        Optional<String> exhausted = snapshot.windows().stream().filter(window -> window.percentUsed() >= 100)
                .map(window -> periodOf(window.resetsAt()))
                .max(Comparator.naturalOrder());
        if (exhausted.isPresent()) {
            return exhausted;
        }
        return rose(paid) ? Optional.of(YearMonth.from(now.atZone(ZoneOffset.UTC)).toString()) : Optional.empty();
    }

    /** The nearest hour: a reset read again may differ by a fraction of a second and is still the same period. */
    private static String periodOf(Optional<Instant> resetsAt) {
        return resetsAt.map(at -> at.plus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.HOURS).toString())
                .orElse(NO_PERIOD);
    }

    private boolean rose(Paid paid) {
        if (previous == null) {
            return false;
        }
        boolean usedRose = paid.used().isPresent() && previous.used().isPresent()
                && paid.used().get().compareTo(previous.used().get()) > 0;
        boolean balanceFell = paid.balance().isPresent() && previous.balance().isPresent()
                && paid.balance().get().compareTo(previous.balance().get()) < 0;
        return usedRose || balanceFell;
    }

    private static Optional<Double> percentOfLimit(Paid paid) {
        if (paid.used().isEmpty() || paid.monthlyLimit().isEmpty() || paid.monthlyLimit().get().signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(paid.used().get().multiply(BigDecimal.valueOf(100))
                .divide(paid.monthlyLimit().get(), 2, RoundingMode.HALF_UP).doubleValue());
    }
}
