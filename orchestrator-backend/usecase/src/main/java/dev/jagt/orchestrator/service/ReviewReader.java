package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Where the facts of a review request come from: the metered headless assistant, reading the host through the MCP
 * tools of whoever runs jagt. Metering lives here so a read costs the same wherever it is asked from.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewReader {

    private final MeteredAssistant assistant;

    /** The review round for {@code reviewRequestUrl}; any paid read is charged to {@code taskId}. */
    public Optional<ReviewFacts> read(String taskId, String reviewRequestUrl) {
        var answer = assistant.readReview(reviewRequestUrl);
        // Charged even when the read came back empty: the call was paid for either way.
        assistant.chargeTask(taskId, answer.usage());
        return paidRead(answer.facts(), ReviewFacts::exists, reviewRequestUrl);
    }

    /**
     * The branches and title of an open request. The cost is RETURNED rather than charged: the source branch this
     * read produces IS the task, so there is nothing to attribute it to yet.
     */
    public Answer<MergeRequestFacts> readRequest(String reviewRequestUrl) {
        var answer = assistant.readMergeRequest(reviewRequestUrl);
        return new Answer<>(paidRead(answer.facts(), MergeRequestFacts::exists, reviewRequestUrl),
                answer.usage());
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
