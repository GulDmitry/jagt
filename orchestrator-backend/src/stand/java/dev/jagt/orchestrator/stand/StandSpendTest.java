package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandSpendTest {

    @Test
    void sumsEveryDayOfAKindIntoOneFigure(@TempDir Path dir) throws Exception {
        Path usage = dir.resolve("usage-by-day.jsonl");
        Files.writeString(usage, """
                {"period":"2026-10-23","kind":"MASTER_REVIEW","usage":{"calls":2,"inputTokens":100,"cachedInputTokens":5,"outputTokens":10,"costUsd":0.5,"maxCallCostUsd":0.3}}
                {"period":"2026-10-24","kind":"MASTER_REVIEW","usage":{"calls":1,"inputTokens":50,"cachedInputTokens":0,"outputTokens":4,"costUsd":0.25,"maxCallCostUsd":0.25}}
                {"period":"2026-10-24","kind":"INTAKE","usage":{"calls":3,"inputTokens":7,"cachedInputTokens":0,"outputTokens":1,"costUsd":0.01,"maxCallCostUsd":0.0}}
                """);

        StandSpend spend = StandSpend.read(dir);

        assertThat(spend.kinds()).containsOnlyKeys("INTAKE", "MASTER_REVIEW")
                .containsEntry("MASTER_REVIEW", new StandSpend.Figures(3, 0.75, 150, 14));
    }

    @Test
    void saysUnknownWhenTheRunMeteredNothing(@TempDir Path dir) {
        StandSpend spend = StandSpend.read(dir);

        assertThat(spend.unknown()).startsWith("unknown: no usage file");
        assertThat(spend.total()).isNull();
    }

    @Test
    void namesTheCauseWhenTheFileCannotBeRead(@TempDir Path dir) throws Exception {
        Path usage = dir.resolve("usage-by-day.jsonl");
        Files.writeString(usage, "not json\n");

        StandSpend spend = StandSpend.read(dir);

        assertThat(spend.unknown()).startsWith("unknown: usage file unreadable: ");
    }

    @Test
    void addsTheTaskSessionsSpendFromStateNextToTheHeadlessKinds(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("usage-by-day.jsonl"), """
                {"period":"2026-10-24","kind":"INTAKE","usage":{"calls":3,"inputTokens":7,"cachedInputTokens":0,"outputTokens":1,"costUsd":0.25,"maxCallCostUsd":0.0}}
                """);
        Files.writeString(dir.resolve("state.json"), """
                {"tasks":{
                  "ABC-1":{"usage":{"calls":9,"inputTokens":1,"cachedInputTokens":0,"outputTokens":1,"costUsd":9.0},
                           "agentSpend":{"usage":{"calls":10,"inputTokens":1000,"cachedInputTokens":500,"outputTokens":100,"costUsd":2.5},"marks":{"a":1}}},
                  "ABC-2":{"agentSpend":{"usage":{"calls":4,"inputTokens":200,"cachedInputTokens":0,"outputTokens":20,"costUsd":0.5},"marks":{}}},
                  "ABC-3":{}}}
                """);

        StandSpend spend = StandSpend.read(dir);

        assertThat(spend.unknown()).isEmpty();
        assertThat(spend.kinds()).containsEntry("SESSIONS", new StandSpend.Figures(14, 3.0, 1200, 120));
        assertThat(spend.total()).isEqualTo(new StandSpend.Figures(17, 3.25, 1207, 121));
    }

    @Test
    void namesTheCauseWhenNoTaskHoldsSessionSpend(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("usage-by-day.jsonl"), "");
        Files.writeString(dir.resolve("state.json"), "{\"tasks\":{\"ABC-1\":{}}}");

        StandSpend spend = StandSpend.read(dir);

        assertThat(spend.unknown()).isEqualTo("unknown: no session spend in state.json");
        assertThat(spend.kinds()).doesNotContainKey("SESSIONS");
    }
}
