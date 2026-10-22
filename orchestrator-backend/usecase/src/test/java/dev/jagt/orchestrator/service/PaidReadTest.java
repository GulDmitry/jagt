package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.protocol.RetryPolicy;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PaidReadTest {

    @Test
    void stopsAskingOnceTheCallerIsInterruptedAndKeepsTheInterruption() {
        AtomicInteger asked = new AtomicInteger();
        RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(1), Duration.ofMinutes(2));
        Thread.currentThread().interrupt();

        Answer<String> answer = PaidRead.untilUsable(policy, "ABC-1", corrections -> {
            asked.incrementAndGet();
            return new Answer<>(Optional.empty(), TokenUsage.NONE);
        }, facts -> List.of(), facts -> true);

        assertThat(Thread.interrupted()).isTrue();
        assertThat(asked).hasValue(1);
        assertThat(answer.facts()).isEmpty();
    }

    @Test
    void asksOnceWhenTheReadGateRefusedATool() {
        AtomicInteger asked = new AtomicInteger();
        RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(1), Duration.ofMinutes(2));

        Answer<String> answer = PaidRead.untilUsable(policy, "ABC-1", corrections -> {
            asked.incrementAndGet();
            return new Answer<>(Optional.empty(), TokenUsage.NONE, "jagt refuses mcp__acme__tool", true);
        }, facts -> List.of(), facts -> true);

        assertThat(asked).hasValue(1);
        assertThat(answer.refused()).isTrue();
        assertThat(answer.failure()).isEqualTo("jagt refuses mcp__acme__tool");
    }
}
