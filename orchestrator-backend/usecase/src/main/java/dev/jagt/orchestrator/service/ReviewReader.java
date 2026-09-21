package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.protocol.RetryPolicy;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Where the facts of a review request come from: the metered headless assistant, reading the host through the MCP
 * tools of whoever runs jagt. Metering lives here so a read costs the same wherever it is asked from.
 */
@Component
@Slf4j
public class ReviewReader {

    private final MeteredAssistant assistant;
    private final RetryPolicy policy;

    @Autowired
    public ReviewReader(MeteredAssistant assistant) {
        this(assistant, RetryPolicy.PAID_READ);
    }

    ReviewReader(MeteredAssistant assistant, RetryPolicy policy) {
        this.assistant = assistant;
        this.policy = policy;
    }

    /** The review round for {@code reviewRequestUrl}; any paid read is charged to {@code taskId}. */
    public Optional<ReviewFacts> read(String taskId, String reviewRequestUrl) {
        var answer = untilRead(reviewRequestUrl, () -> assistant.readReview(reviewRequestUrl));
        // Charged even when the read came back empty: every call was paid for either way.
        assistant.chargeTask(taskId, answer.usage());
        return paidRead(answer.facts(), ReviewFacts::exists, reviewRequestUrl);
    }

    /**
     * The branches and title of an open request. The cost is RETURNED rather than charged: the source branch this
     * read produces IS the task, so there is nothing to attribute it to yet.
     */
    public Answer<MergeRequestFacts> readRequest(String reviewRequestUrl) {
        var answer = untilRead(reviewRequestUrl, () -> assistant.readMergeRequest(reviewRequestUrl));
        return new Answer<>(paidRead(answer.facts(), MergeRequestFacts::exists, reviewRequestUrl),
                answer.usage());
    }

    /**
     * Sends the read again while it comes back with nothing to read, up to the policy. A host that ANSWERS "no
     * such request" is believed on the spot — that is the one case the words belong to, and paying to hear it
     * three times buys nothing.
     */
    private <T> Answer<T> untilRead(String url, java.util.function.Supplier<Answer<T>> ask) {
        long deadline = System.nanoTime() + policy.budget().toNanos();
        Answer<T> answer = Answer.unavailable();
        TokenUsage spent = TokenUsage.NONE;
        for (int attempt = 1; attempt <= policy.attempts(); attempt++) {
            answer = ask.get();
            spent = spent.plus(answer.usage());
            if (answer.facts().isPresent()) {
                return new Answer<>(answer.facts(), spent);
            }
            log.atWarn().setMessage("read came back unreadable")
                    .addKeyValue("ref", url)
                    .addKeyValue("attempt", attempt)
                    .addKeyValue("limit", policy.attempts())
                    .log();
            if (policy.lastAttempt(attempt) || System.nanoTime() > deadline || !pause()) {
                break;
            }
        }
        return new Answer<>(answer.facts(), spent);
    }

    private boolean pause() {
        try {
            Thread.sleep(policy.between());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void charge(String taskId, TokenUsage usage) {
        assistant.chargeTask(taskId, usage);
    }

    /**
     * A paid read answering "no such request" is either the truth or a read with no tool to read it with, so the
     * CLI is asked which of its MCP servers are down.
     */
    private <T> Optional<T> paidRead(Optional<T> facts, Predicate<T> exists, String url) {
        if (facts.isPresent() && !exists.test(facts.get())) {
            Optional<List<String>> broken = assistant.brokenMcpServers();
            log.atWarn().setMessage("read says not found")
                    .addKeyValue("ref", url)
                    .addKeyValue("cause", "exists=false")
                    .addKeyValue("mcp", broken.map(down -> down.isEmpty() ? "none-reported" : "down").orElse("unknown"))
                    .addKeyValue("servers", broken.map(down -> String.join(", ", down)).orElse(""))
                    .log();
        }
        return facts;
    }
}
