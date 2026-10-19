package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spend summed per period and kind beside `state.json`, so it outlives a restart: a day for {@link #DAYS}, then its
 * month for {@link #MONTHS}, then its year, folded on every write so the file stays bounded.
 */
@Component
@Slf4j
public class UsageLedger {

    static final int DAYS = 90;
    static final int MONTHS = 24;

    /**
     * {@code period} is a day, a month or a year: 2026-10-13, 2026-10 or 2026. {@code maxCallCostUsd} is the dearest
     * single call, 0 on a row written before it was kept.
     */
    public record Spend(String period, AssistantCallKind kind, TokenUsage usage, double maxCallCostUsd) {
    }

    private final ObjectMapper mapper;
    private final Path file;
    private final Clock clock;

    public UsageLedger(ObjectMapper mapper, OrchestratorPaths paths, Clock clock) {
        this.mapper = mapper;
        this.file = paths.stateFile().resolveSibling("usage-by-day.jsonl");
        this.clock = clock;
    }

    /** A line it cannot parse is written back as it was, and an unreadable file is left untouched. */
    public synchronized void add(AssistantCallKind kind, TokenUsage usage) {
        try {
            List<String> kept = new ArrayList<>();
            Map<String, Spend> periods = new LinkedHashMap<>();
            LocalDate today = LocalDate.now(clock);
            for (String line : Files.isRegularFile(file) ? Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank()).toList() : List.<String>of()) {
                parsed(line).ifPresentOrElse(spend -> fold(periods, period(start(spend.period()), today), spend.kind(),
                        spend.usage(), spend.maxCallCostUsd()), () -> kept.add(line));
            }
            fold(periods, today.toString(), kind, usage, usage.costUsd());
            periods.values().forEach(spend -> kept.add(mapper.writeValueAsString(written(spend))));
            Path written = Files.createTempFile(file.getParent(), "usage-by-day", ".tmp");
            Files.write(written, kept, StandardCharsets.UTF_8);
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            log.atWarn().setMessage("usage not recorded").addKeyValue("kind", kind)
                    .addKeyValue("cause", e.toString()).addKeyValue("effect", "day total short").log();
        }
    }

    private static void fold(Map<String, Spend> periods, String period, AssistantCallKind kind, TokenUsage usage,
                             double maxCallCostUsd) {
        periods.merge(period + kind.name(), new Spend(period, kind, usage, maxCallCostUsd),
                (was, added) -> new Spend(period, kind, was.usage().plus(added.usage()),
                        Math.max(was.maxCallCostUsd(), added.maxCallCostUsd())));
    }

    private ObjectNode written(Spend spend) {
        ObjectNode usage = mapper.valueToTree(spend.usage());
        usage.put("maxCallCostUsd", spend.maxCallCostUsd());
        ObjectNode row = mapper.createObjectNode();
        row.put("period", spend.period());
        row.put("kind", spend.kind().name());
        row.set("usage", usage);
        return row;
    }

    static String period(LocalDate day, LocalDate today) {
        if (day.isAfter(today.minusDays(DAYS))) {
            return day.toString();
        }
        return day.isAfter(today.minusMonths(MONTHS)) ? YearMonth.from(day).toString() : Year.from(day).toString();
    }

    private static LocalDate start(String period) {
        return switch (period.length()) {
            case 4 -> Year.parse(period).atDay(1);
            case 7 -> YearMonth.parse(period).atDay(1);
            default -> LocalDate.parse(period);
        };
    }

    private Optional<Spend> parsed(String line) {
        try {
            JsonNode row = mapper.readTree(line);
            JsonNode usage = row.required("usage");
            return Optional.of(new Spend(row.required("period").asString(),
                    AssistantCallKind.valueOf(row.required("kind").asString()),
                    mapper.treeToValue(usage, TokenUsage.class), usage.path("maxCallCostUsd").asDouble(0)));
        } catch (RuntimeException e) {
            log.atWarn().setMessage("usage line unreadable").addKeyValue("file", file)
                    .addKeyValue("cause", e.toString()).log();
            return Optional.empty();
        }
    }
}
