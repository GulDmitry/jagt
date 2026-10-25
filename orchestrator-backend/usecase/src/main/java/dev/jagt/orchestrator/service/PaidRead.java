package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.protocol.RetryPolicy;
import dev.jagt.orchestrator.protocol.Violation;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.backoff.FixedBackOff;

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
        Attempts<T> attempts = new Attempts<>();
        RetryTemplate template = new RetryTemplate(org.springframework.core.retry.RetryPolicy.builder()
                .backOff(new FixedBackOff(policy.between().toMillis(), policy.attempts() - 1L))
                .timeout(policy.budget())
                .includes(Unusable.class)
                .build());
        try {
            template.execute(() -> {
                attempts.number++;
                Answer<T> answer = ask.apply(attempts.corrections);
                attempts.spent = attempts.spent.plus(answer.usage());
                attempts.last = answer;
                if (answer.facts().filter(usable).isPresent()) {
                    return answer;
                }
                attempts.corrections = answer.facts()
                        .map(facts -> violations.apply(facts).stream().map(Violation::toString).toList())
                        .orElse(List.of());
                log.atWarn().setMessage("paid read unusable")
                        .addKeyValue("ref", ref)
                        .addKeyValue("cause", cause(answer, attempts.corrections))
                        .addKeyValue("attempt", attempts.number)
                        .addKeyValue("limit", policy.attempts())
                        .log();
                throw answer.refused() ? new Refused() : new Unusable();
            });
        } catch (RetryException exhausted) {
            // The last answer is what the caller judges; the attempts hold it.
        }
        return new Answer<>(attempts.last.facts(), attempts.spent, attempts.last.failure(), attempts.last.refused());
    }

    private static String cause(Answer<?> answer, List<String> corrections) {
        if (answer.refused()) {
            return answer.failure().isBlank() ? "the read gate refused a tool" : answer.failure();
        }
        return answer.facts().isEmpty() ? "no answer" : corrections.isEmpty() ? "not usable" : String.join("; ", corrections);
    }

    private static final class Attempts<T> {
        private int number;
        private Answer<T> last = Answer.unavailable();
        private TokenUsage spent = TokenUsage.NONE;
        private List<String> corrections = List.of();
    }

    private static final class Unusable extends RuntimeException {
        Unusable() {
            super(null, null, false, false);
        }
    }

    private static final class Refused extends RuntimeException {
        Refused() {
            super(null, null, false, false);
        }
    }
}
