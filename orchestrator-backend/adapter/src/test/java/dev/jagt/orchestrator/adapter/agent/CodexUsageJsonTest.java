package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SubscriptionUsage;
import dev.jagt.orchestrator.port.SubscriptionUsage.Paid;
import dev.jagt.orchestrator.port.SubscriptionUsage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CodexUsageJsonTest {

    @Test
    void namesEachWindowByItsLengthAndReadsThePrepaidBalance() {
        var answer = CodexUsageJson.parse("""
                {"plan_type": "plus",
                 "rate_limit": {"primary_window": {"used_percent": 31, "limit_window_seconds": 18000,
                                                   "reset_at": 1791043200},
                                "secondary_window": {"used_percent": 8, "limit_window_seconds": 604800,
                                                     "reset_at": 1791475200}},
                 "credits": {"has_credits": true, "unlimited": false, "balance": "120.5"}}
                """);

        assertThat(answer).isEqualTo(new SubscriptionUsage.Read(new SubscriptionUsage.Snapshot(List.of(
                new Window("5h", 31, Optional.of(Instant.ofEpochSecond(1791043200))),
                new Window("week", 8, Optional.of(Instant.ofEpochSecond(1791475200)))),
                Optional.of(new Paid(Optional.empty(), Optional.empty(), "credits",
                        Optional.of(new BigDecimal("120.5")))))));
    }

    @Test
    void answersUnreadableRatherThanZeroWhenTheShapeChanged() {
        var answer = CodexUsageJson.parse("""
                {"rate_limit": {"primary_window": {"used": 31, "limit_window_seconds": 18000}}}
                """);

        assertThat(answer).isEqualTo(new SubscriptionUsage.Unreadable(
                "usage answer changed shape: used_percent is not a number"));
    }

    @Test
    void answersUnreadableWhenNoWindowIsGiven() {
        var answer = CodexUsageJson.parse("""
                {"rate_limit": {}}
                """);

        assertThat(answer).isEqualTo(new SubscriptionUsage.Unreadable("usage answer carried no window"));
    }

    @ParameterizedTest
    @CsvSource({
            "3600, 1h",
            "82800, 23h",
            "86400, 1d",
            "90000, 25h",
            "172800, 2d",
    })
    void namesAWindowInWholeDaysOnlyWhenItIsOne(long seconds, String name) {
        var answer = CodexUsageJson.parse("""
                {"rate_limit": {"primary_window": {"used_percent": 1, "limit_window_seconds": %d}}}
                """.formatted(seconds));

        assertThat(answer).isEqualTo(new SubscriptionUsage.Read(new SubscriptionUsage.Snapshot(
                List.of(new Window(name, 1, Optional.empty())), Optional.empty())));
    }

    @Test
    void answersUnreadableWhenTheAnswerIsNotJson() {
        var answer = CodexUsageJson.parse("<html>");

        assertThat(answer).isEqualTo(new SubscriptionUsage.Unreadable("usage answer is not JSON"));
    }
}
