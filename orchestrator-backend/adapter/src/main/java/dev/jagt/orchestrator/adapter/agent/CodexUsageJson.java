package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SubscriptionUsage;
import dev.jagt.orchestrator.port.SubscriptionUsage.Paid;
import dev.jagt.orchestrator.port.SubscriptionUsage.Window;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The undocumented answer of ChatGPT's usage endpoint: a shape it does not know is unreadable, never zero. */
final class CodexUsageJson {

    private static final JsonMapper JSON = new JsonMapper();

    private CodexUsageJson() {
    }

    static SubscriptionUsage.Answer parse(String json) {
        try {
            JsonNode root = JSON.readTree(json);
            JsonNode limits = root.path("rate_limit");
            List<Window> windows = new ArrayList<>();
            for (String field : List.of("primary_window", "secondary_window")) {
                JsonNode window = limits.path(field);
                if (window.isObject()) {
                    windows.add(new Window(name(number(window, "limit_window_seconds")),
                            number(window, "used_percent"), window.path("reset_at").isNumber()
                            ? Optional.of(Instant.ofEpochSecond(window.path("reset_at").longValue())) : Optional.empty()));
                }
            }
            if (windows.isEmpty()) {
                return new SubscriptionUsage.Unreadable("usage answer carried no window");
            }
            return new SubscriptionUsage.Read(new SubscriptionUsage.Snapshot(windows, credits(root.path("credits"))));
        } catch (JacksonException e) {
            return new SubscriptionUsage.Unreadable("usage answer is not JSON");
        } catch (IllegalArgumentException e) {
            return new SubscriptionUsage.Unreadable("usage answer changed shape: " + e.getMessage());
        }
    }

    private static String name(double seconds) {
        Duration window = Duration.ofSeconds((long) seconds);
        if (window.equals(Duration.ofDays(7))) {
            return "week";
        }
        return window.toHours() >= 24 && window.toHours() % 24 == 0 ? window.toDays() + "d" : window.toHours() + "h";
    }

    private static Optional<Paid> credits(JsonNode credits) {
        if (!credits.path("has_credits").asBoolean(false) || credits.path("unlimited").asBoolean(false)) {
            return Optional.empty();
        }
        String balance = credits.path("balance").asString("");
        if (balance.isBlank()) {
            throw new IllegalArgumentException("credits.balance is missing");
        }
        return Optional.of(new Paid(Optional.empty(), Optional.empty(), "credits", Optional.of(new BigDecimal(balance))));
    }

    private static double number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            throw new IllegalArgumentException(field + " is not a number");
        }
        return value.doubleValue();
    }
}
