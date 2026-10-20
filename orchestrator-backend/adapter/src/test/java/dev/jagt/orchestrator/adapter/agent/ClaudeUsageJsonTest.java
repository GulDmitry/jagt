package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SubscriptionUsage;
import dev.jagt.orchestrator.port.SubscriptionUsage.Paid;
import dev.jagt.orchestrator.port.SubscriptionUsage.Window;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeUsageJsonTest {

    @Test
    void readsEachWindowItsResetAndPaidUsageInDollarsFromCents() {
        var answer = ClaudeUsageJson.parse("""
                {"five_hour": {"utilization": 42.0, "resets_at": "2026-10-10T16:00:00.000000+00:00"},
                 "seven_day": {"utilization": 63.5, "resets_at": "2026-10-14T09:00:00+00:00"},
                 "seven_day_opus": null,
                 "seven_day_sonnet": {"utilization": 12.0, "resets_at": null},
                 "extra_usage": {"is_enabled": true, "monthly_limit": 50000, "used_credits": 1234,
                                 "utilization": 2.5, "currency": "USD"}}
                """);

        assertThat(answer).isEqualTo(new SubscriptionUsage.Read(new SubscriptionUsage.Snapshot(List.of(
                new Window("5h", 42.0, Optional.of(Instant.parse("2026-10-10T16:00:00Z"))),
                new Window("week", 63.5, Optional.of(Instant.parse("2026-10-14T09:00:00Z"))),
                new Window("week-sonnet", 12.0, Optional.empty())),
                Optional.of(new Paid(Optional.of(new BigDecimal("12.34")), Optional.of(new BigDecimal("500.00")),
                        "USD", Optional.empty())))));
    }

    @Test
    void addsAModelWindowOnlyTheLimitsListCarries() {
        var answer = (SubscriptionUsage.Read) ClaudeUsageJson.parse("""
                {"five_hour": {"utilization": 10, "resets_at": null},
                 "limits": [{"kind": "seven_day", "group": "model", "percent": 77, "resets_at": null,
                             "scope": {"model": {"display_name": "Fable"}}, "is_active": true},
                            {"kind": "five_hour", "percent": 10, "resets_at": null, "is_active": true}]}
                """);

        assertThat(answer.snapshot().windows()).extracting(Window::name).containsExactly("5h", "week-fable");
    }

    @Test
    void leavesOutAWindowNoUsageHasStartedYet() {
        var answer = (SubscriptionUsage.Read) ClaudeUsageJson.parse("""
                {"five_hour": {"utilization": null, "resets_at": null},
                 "seven_day": {"utilization": 40, "resets_at": null},
                 "limits": [{"kind": "seven_day", "percent": null, "resets_at": null,
                             "scope": {"model": {"display_name": "Fable"}}, "is_active": true}]}
                """);

        assertThat(answer.snapshot().windows()).extracting(Window::name).containsExactly("week");
    }

    @Test
    void answersNoPaidUsageWhileExtraUsageIsOff() {
        var answer = (SubscriptionUsage.Read) ClaudeUsageJson.parse("""
                {"five_hour": {"utilization": 10, "resets_at": null},
                 "extra_usage": {"is_enabled": false, "monthly_limit": null, "used_credits": null}}
                """);

        assertThat(answer.snapshot().paid()).isEmpty();
    }

    @Test
    void answersUnreadableRatherThanZeroWhenTheShapeChanged() {
        var answer = ClaudeUsageJson.parse("""
                {"five_hour": {"utilization": "high", "resets_at": null}}
                """);

        assertThat(answer).isEqualTo(new SubscriptionUsage.Unreadable(
                "usage answer changed shape: five_hour.utilization is not a number"));
    }

    @Test
    void answersUnreadableWhenNoWindowIsThere() {
        var answer = ClaudeUsageJson.parse("{\"windows\": []}");

        assertThat(answer).isEqualTo(new SubscriptionUsage.Unreadable("usage answer carried no window"));
    }

    @Test
    void answersUnreadableWhenTheAnswerIsNotJson() {
        var answer = ClaudeUsageJson.parse("<html>");

        assertThat(answer).isEqualTo(new SubscriptionUsage.Unreadable("usage answer is not JSON"));
    }
}
