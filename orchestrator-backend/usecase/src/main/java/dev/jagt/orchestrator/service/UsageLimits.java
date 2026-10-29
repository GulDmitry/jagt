package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.SubscriptionUsage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

/** The last answer the subscription gave; empty until a read ran, and always empty for a CLI with no reader. */
@Component
@RequiredArgsConstructor
public class UsageLimits {

    public record Reading(SubscriptionUsage.Answer answer, Instant at) {
    }

    private final Optional<SubscriptionUsage> source;
    private volatile @Nullable Reading last;

    public Optional<Reading> refresh(Instant now) {
        source.ifPresent(usage -> last = new Reading(usage.read(), now));
        return last();
    }

    public Optional<Reading> last() {
        return Optional.ofNullable(last);
    }

    public static String spent(SubscriptionUsage.Paid paid) {
        String currency = " " + paid.currency();
        String used = paid.used().map(amount -> money(amount) + paid.monthlyLimit()
                .map(limit -> " of " + money(limit)).orElse("") + currency).orElse("");
        String balance = paid.balance().map(amount -> "balance " + money(amount) + currency).orElse("");
        return used.isEmpty() || balance.isEmpty() ? used + balance : used + ", " + balance;
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
