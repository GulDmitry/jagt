package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSpendTest {

    @Test
    void addsWhatALogSpentToTheTotalAndRemembersWhereItStopped() {
        AgentSpend spend = AgentSpend.NONE
                .plus(TokenUsage.ofCall(5, 100, 10, 0), "/logs/one.jsonl", 300)
                .plus(TokenUsage.ofCall(7, 200, 20, 0), "/logs/one.jsonl", 800);

        assertThat(spend.usageOrNone()).isEqualTo(new TokenUsage(2, 12, 300, 30, 0));
        assertThat(spend.markFor("/logs/one.jsonl")).isEqualTo(800);
        assertThat(spend.markFor("/logs/other.jsonl")).isZero();
    }

    /** A task that runs long opens many sessions, and every mark of them was kept and rewritten for good. */
    @Test
    void keepsTheMarksOfTheLastTenLogsAndDropsTheOldest() {
        AgentSpend spend = AgentSpend.NONE;
        for (int session = 1; session <= 12; session++) {
            spend = spend.plus(TokenUsage.ofCall(1, 0, 1, 0), "/logs/" + session + ".jsonl", session);
        }

        assertThat(spend.marks()).hasSize(10);
        assertThat(spend.markFor("/logs/1.jsonl")).isZero();
        assertThat(spend.markFor("/logs/12.jsonl")).isEqualTo(12);
        assertThat(spend.usageOrNone().calls()).isEqualTo(12);
    }
}
