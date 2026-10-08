package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CodeHostAssistant;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.protocol.MergeRequestRead;
import dev.jagt.orchestrator.protocol.RetryPolicy;
import dev.jagt.orchestrator.protocol.ReviewRead;
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
 * Where the facts of a review request come from: the headless assistant, reading the host through the MCP tools of
 * whoever runs jagt.
 */
@Component
@Slf4j
public class ReviewReader {

    private final CodeHostAssistant assistant;
    private final McpHealth mcp;
    private final UsageTracker usage;
    private final RetryPolicy policy;

    @Autowired
    public ReviewReader(CodeHostAssistant assistant, McpHealth mcp, UsageTracker usage) {
        this(assistant, mcp, usage, RetryPolicy.PAID_READ);
    }

    ReviewReader(CodeHostAssistant assistant, McpHealth mcp, UsageTracker usage, RetryPolicy policy) {
        this.assistant = assistant;
        this.mcp = mcp;
        this.usage = usage;
        this.policy = policy;
    }

    /** The review round for {@code reviewRequestUrl}; any paid read is charged to {@code taskId}. */
    public Optional<ReviewFacts> read(String taskId, String reviewRequestUrl) {
        var answer = PaidRead.untilUsable(policy, reviewRequestUrl,
                corrections -> assistant.readReview(reviewRequestUrl),
                ReviewRead::violations, facts -> ReviewRead.violations(facts).isEmpty());
        // Charged even when the read came back empty: every call was paid for either way.
        usage.chargeTask(taskId, answer.usage());
        return paidRead(answer.facts(), ReviewFacts::exists, reviewRequestUrl);
    }

    /**
     * The branches and title of an open request. The cost is RETURNED rather than charged: the source branch this
     * read produces IS the task, so there is nothing to attribute it to yet.
     */
    public Answer<MergeRequestFacts> readRequest(String reviewRequestUrl) {
        var answer = PaidRead.untilUsable(policy, reviewRequestUrl,
                corrections -> assistant.readMergeRequest(reviewRequestUrl),
                MergeRequestRead::violations, facts -> MergeRequestRead.violations(facts).isEmpty());
        return new Answer<>(paidRead(answer.facts(), MergeRequestFacts::exists, reviewRequestUrl),
                answer.usage());
    }

    public void charge(String taskId, TokenUsage usage) {
        this.usage.chargeTask(taskId, usage);
    }

    /**
     * A paid read answering "no such request" is either the truth or a read with no tool to read it with, so the
     * CLI is asked which of its MCP servers are down.
     */
    private <T> Optional<T> paidRead(Optional<T> facts, Predicate<T> exists, String url) {
        if (facts.isPresent() && !exists.test(facts.get())) {
            Optional<List<String>> broken = mcp.brokenServers();
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
