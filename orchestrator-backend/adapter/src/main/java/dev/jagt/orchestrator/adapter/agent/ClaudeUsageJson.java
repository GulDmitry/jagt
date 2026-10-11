package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SubscriptionUsage;
import dev.jagt.orchestrator.port.SubscriptionUsage.Paid;
import dev.jagt.orchestrator.port.SubscriptionUsage.Window;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The undocumented answer of Claude's OAuth usage endpoint: a shape it does not know is unreadable, never zero. */
final class ClaudeUsageJson {

    private static final JsonMapper JSON = new JsonMapper();
    private static final Map<String, String> WINDOWS = Map.of("five_hour", "5h", "seven_day", "week",
            "seven_day_opus", "week-opus", "seven_day_sonnet", "week-sonnet");
    private static final List<String> ORDER = List.of("five_hour", "seven_day", "seven_day_opus", "seven_day_sonnet");

    private ClaudeUsageJson() {
    }

    static SubscriptionUsage.Answer parse(String json) {
        try {
            JsonNode root = JSON.readTree(json);
            List<Window> windows = new ArrayList<>();
            for (String field : ORDER) {
                JsonNode window = root.path(field);
                if (window.isObject() && !window.path("utilization").isNull()) {
                    windows.add(new Window(WINDOWS.get(field), number(window, "utilization", field),
                            instant(window.path("resets_at"))));
                }
            }
            for (JsonNode limit : root.path("limits")) {
                String name = name(limit);
                if (limit.path("is_active").asBoolean(true) && !limit.path("percent").isNull()
                        && windows.stream().noneMatch(window -> window.name().equals(name))) {
                    windows.add(new Window(name, number(limit, "percent", "limits"), instant(limit.path("resets_at"))));
                }
            }
            if (windows.isEmpty()) {
                return new SubscriptionUsage.Unreadable("usage answer carried no window");
            }
            return new SubscriptionUsage.Read(new SubscriptionUsage.Snapshot(windows, paid(root.path("extra_usage"))));
        } catch (JacksonException e) {
            return new SubscriptionUsage.Unreadable("usage answer is not JSON");
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return new SubscriptionUsage.Unreadable("usage answer changed shape: " + e.getMessage());
        }
    }

    private static String name(JsonNode limit) {
        String kind = limit.path("kind").asString("");
        String base = WINDOWS.getOrDefault(kind, kind);
        String model = limit.path("scope").path("model").path("display_name").asString("");
        return model.isBlank() ? base : base + "-" + model.strip().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private static Optional<Paid> paid(JsonNode extra) {
        if (!extra.path("is_enabled").asBoolean(false)) {
            return Optional.empty();
        }
        return Optional.of(new Paid(Optional.of(cents(extra, "used_credits")), Optional.of(cents(extra, "monthly_limit")),
                extra.path("currency").asString("USD"), Optional.empty()));
    }

    private static BigDecimal cents(JsonNode node, String field) {
        return numeric(node, field, "extra_usage").decimalValue().movePointLeft(2);
    }

    private static double number(JsonNode node, String field, String in) {
        return numeric(node, field, in).doubleValue();
    }

    private static JsonNode numeric(JsonNode node, String field, String in) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            throw new IllegalArgumentException(in + "." + field + " is not a number");
        }
        return value;
    }

    private static Optional<Instant> instant(JsonNode value) {
        return value.isString() ? Optional.of(OffsetDateTime.parse(value.asString()).toInstant()) : Optional.empty();
    }
}
