package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class UsageLedgerTest {

    @Test
    void sumsADaysCallsOfOneKindIntoOneLine(@TempDir Path root) throws Exception {
        UsageLedger ledger = new UsageLedger(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())),
                Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC));

        ledger.add(AssistantCallKind.MASTER_REVIEW, TokenUsage.ofCall(40_000, 0, 900, 0.30));
        ledger.add(AssistantCallKind.MASTER_REVIEW, TokenUsage.ofCall(2_000, 50_000, 800, 0.05));

        assertThat(Files.readAllLines(root.resolve("usage-by-day.jsonl"))).singleElement().asString()
                .contains("\"period\":\"2026-10-13\"", "\"kind\":\"MASTER_REVIEW\"", "\"calls\":2",
                        "\"cachedInputTokens\":50000");
    }

    @Test
    void foldsDaysPastNinetyIntoTheirMonthSoTheFileStaysBounded(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("usage-by-day.jsonl"), """
                {"period":"2026-06-02","kind":"ROUTE","usage":{"calls":1,"inputTokens":100,"cachedInputTokens":0,"outputTokens":0,"costUsd":0,"coldStarts":0,"warmStarts":0}}
                {"period":"2026-06-20","kind":"ROUTE","usage":{"calls":2,"inputTokens":300,"cachedInputTokens":0,"outputTokens":0,"costUsd":0,"coldStarts":0,"warmStarts":0}}
                """);
        UsageLedger ledger = new UsageLedger(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())),
                Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC));

        ledger.add(AssistantCallKind.MASTER_REVIEW, TokenUsage.ofCall(1_000, 0, 10, 0.01));

        assertThat(Files.readAllLines(root.resolve("usage-by-day.jsonl"))).hasSize(2).first().asString()
                .contains("\"period\":\"2026-06\"", "\"calls\":3", "\"inputTokens\":400");
    }

    @Test
    void keepsTheDearestSingleCallOfTheDay(@TempDir Path root) throws Exception {
        UsageLedger ledger = new UsageLedger(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())),
                Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC));

        ledger.add(AssistantCallKind.MASTER_REVIEW, TokenUsage.ofCall(40_000, 0, 900, 0.30));
        ledger.add(AssistantCallKind.MASTER_REVIEW, TokenUsage.ofCall(2_000, 0, 800, 0.05));

        assertThat(Files.readAllLines(root.resolve("usage-by-day.jsonl"))).singleElement().asString()
                .contains("\"costUsd\":0.35", "\"maxCallCostUsd\":0.3");
    }

    @Test
    void readsARowWrittenBeforeTheDearestCallWasKept(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("usage-by-day.jsonl"), """
                {"period":"2026-10-13","kind":"ROUTE","usage":{"calls":2,"inputTokens":300,"cachedInputTokens":0,"outputTokens":0,"costUsd":0.5,"coldStarts":0,"warmStarts":0}}
                """);
        UsageLedger ledger = new UsageLedger(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())),
                Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC));

        ledger.add(AssistantCallKind.ROUTE, TokenUsage.ofCall(1_000, 0, 10, 0.07));

        assertThat(Files.readAllLines(root.resolve("usage-by-day.jsonl"))).singleElement().asString()
                .contains("\"calls\":3", "\"maxCallCostUsd\":0.07");
    }

    @ParameterizedTest
    @CsvSource({"2026-07-16, 2026-07-16", "2026-07-15, 2026-07", "2024-10-14, 2024-10", "2024-10-13, 2024"})
    void namesTheDayItsMonthOrItsYearByItsAge(LocalDate day, String period) {
        assertThat(UsageLedger.period(day, LocalDate.parse("2026-10-13"))).isEqualTo(period);
    }

    @Test
    void keepsALineItCannotReadRatherThanDroppingIt(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("usage-by-day.jsonl"), "{\"period\":\"2026-09-01\",\"kind\":\"GONE_KIND\"}\n");
        UsageLedger ledger = new UsageLedger(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())),
                Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC));

        ledger.add(AssistantCallKind.ROUTE, TokenUsage.ofCall(1_000, 0, 10, 0.01));

        assertThat(Files.readAllLines(root.resolve("usage-by-day.jsonl")))
                .contains("{\"period\":\"2026-09-01\",\"kind\":\"GONE_KIND\"}").hasSize(2);
    }
}
