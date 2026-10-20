package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.SubscriptionUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UsageLimitsTest {

    @Test
    void namesWhatWasUsedOfTheMonthlyLimitWithItsCurrency() {
        var paid = new SubscriptionUsage.Paid(Optional.of(new BigDecimal("12.5")),
                Optional.of(new BigDecimal("50")), "USD", Optional.empty());

        assertThat(UsageLimits.spent(paid)).isEqualTo("12.50 of 50.00 USD");
    }

    @Test
    void namesOnlyTheBalanceWhereNothingWasUsed() {
        var paid = new SubscriptionUsage.Paid(Optional.empty(), Optional.empty(), "EUR",
                Optional.of(new BigDecimal("7")));

        assertThat(UsageLimits.spent(paid)).isEqualTo("balance 7.00 EUR");
    }

    @Test
    void joinsWhatWasUsedAndTheBalanceWithAComma() {
        var paid = new SubscriptionUsage.Paid(Optional.of(new BigDecimal("3")), Optional.empty(), "USD",
                Optional.of(new BigDecimal("7")));

        assertThat(UsageLimits.spent(paid)).isEqualTo("3.00 USD, balance 7.00 USD");
    }
}
