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

    @Test
    void keepsTheMarksOfTheLastTenLogsAndDropsTheOldest() {
        AgentSpend spend = AgentSpend.NONE
                .plus(TokenUsage.ofCall(1, 0, 1, 0), "/logs/1.jsonl", 1)
                .plus(TokenUsage.ofCall(1, 0, 1, 0), "/logs/2.jsonl", 2)
                .plus(TokenUsage.ofCall(1, 0, 1, 0), "/logs/3.jsonl", 3)
                .plus(TokenUsage.ofCall(1, 0, 1, 0), "/logs/4.jsonl", 4)
                .plus(TokenUsage.ofCall(1, 0, 1, 0), "/logs/5.jsonl", 5)
                .plus(TokenUsage.ofCall(1, 0, 1, 0), "/logs/6.jsonl", 6)
                .plus(TokenUsage.ofCall(1, 0, 1, 0), "/logs/7.jsonl", 7)
                .plus(TokenUsage.ofCall(1, 0, 1, 0), "/logs/8.jsonl", 8)
                .plus(TokenUsage.ofCall(1, 0, 1, 0), "/logs/9.jsonl", 9)
                .plus(TokenUsage.ofCall(1, 0, 1, 0), "/logs/10.jsonl", 10)
                .plus(TokenUsage.ofCall(1, 0, 1, 0), "/logs/11.jsonl", 11)
                .plus(TokenUsage.ofCall(1, 0, 1, 0), "/logs/12.jsonl", 12);

        assertThat(spend.marks()).hasSize(10);
        assertThat(spend.markFor("/logs/1.jsonl")).isZero();
        assertThat(spend.markFor("/logs/12.jsonl")).isEqualTo(12);
        assertThat(spend.usageOrNone().calls()).isEqualTo(12);
    }
}
