package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.protocol.RetryPolicy;
import dev.jagt.orchestrator.protocol.Violation;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A model's non-answer is indistinguishable from a tool it never found, so the read is sent again carrying what was
 * wrong with the last answer — the same question returns the same answer. Every attempt is paid for, so all are
 * returned as one spend, and an exhausted policy returns the last answer for the caller to judge.
 */
@Slf4j
final class PaidRead {

    private PaidRead() {
    }

    static <T> Answer<T> untilUsable(RetryPolicy policy, String ref, Function<List<String>, Answer<T>> ask,
                                     Function<T, List<Violation>> violations, Predicate<T> usable) {
        long deadline = System.nanoTime() + policy.budget().toNanos();
        Answer<T> answer = Answer.unavailable();
        TokenUsage spent = TokenUsage.NONE;
        List<String> corrections = List.of();
        for (int attempt = 1; attempt <= policy.attempts(); attempt++) {
            answer = ask.apply(corrections);
            spent = spent.plus(answer.usage());
            if (answer.facts().filter(usable).isPresent()) {
                return new Answer<>(answer.facts(), spent);
            }
            corrections = answer.facts()
                    .map(facts -> violations.apply(facts).stream().map(Violation::toString).toList())
                    .orElse(List.of());
            log.atWarn().setMessage("paid read unusable")
                    .addKeyValue("ref", ref)
                    .addKeyValue("cause", answer.facts().isEmpty() ? "no answer"
                            : corrections.isEmpty() ? "not usable" : String.join("; ", corrections))
                    .addKeyValue("attempt", attempt)
                    .addKeyValue("limit", policy.attempts())
                    .log();
            if (policy.lastAttempt(attempt) || System.nanoTime() > deadline || !pause(policy)) {
                break;
            }
        }
        return new Answer<>(answer.facts(), spent);
    }

    private static boolean pause(RetryPolicy policy) {
        try {
            Thread.sleep(policy.between());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
