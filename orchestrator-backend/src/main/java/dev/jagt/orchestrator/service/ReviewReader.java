package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.assistant.MasterAssistant.Answer;
import dev.jagt.orchestrator.codehost.CodeHost;
import dev.jagt.orchestrator.model.MergeRequestFacts;
import dev.jagt.orchestrator.model.ReviewFacts;
import dev.jagt.orchestrator.model.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Where the facts of a review request come from — its round for a sweep, its branches for a task re-entering on
 * it: a configured {@link CodeHost}'s own API when it can read that URL (free, instant), otherwise the metered
 * headless assistant (a full model call, the dominant per-task cost).
 *
 * <p>There is deliberately NO fallback from a failed REST read to the assistant. A code host that claims a URL
 * owns it: falling back would spend money invisibly on every broken token or unreachable host, which is exactly
 * the kind of quiet spend this seam exists to remove — and it would hide the misconfiguration behind a working
 * sweep. A REST failure surfaces as an unreadable review, which the human sees.
 *
 * <p>Metering lives here rather than in the caller so that the two sources are interchangeable at the call
 * site: a free read charges nothing, a paid one is always booked to the task.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewReader {

    private final List<CodeHost> codeHosts;
    private final MeteredAssistant assistant;

    /** The review round for {@code reviewRequestUrl}; any paid read is charged to {@code taskId}. */
    public Optional<ReviewFacts> read(String taskId, String reviewRequestUrl) {
        Optional<CodeHost> host = claiming(reviewRequestUrl);
        if (host.isPresent()) {
            return host.get().readReview(reviewRequestUrl);
        }
        var answer = assistant.readReview(reviewRequestUrl);
        // Charged even when the read came back empty: the call was paid for either way, and the poll repeats
        // up to hourly for a day, so an uncharged failure would understate what the task actually costs.
        assistant.chargeTask(taskId, answer.usage());
        return answer.facts();
    }

    /**
     * The branches and title of an open request. The cost is RETURNED rather than charged: the source branch
     * this read produces IS the task, so there is nothing to attribute it to until the caller has created one.
     */
    public Answer<MergeRequestFacts> readRequest(String reviewRequestUrl) {
        Optional<CodeHost> host = claiming(reviewRequestUrl);
        if (host.isPresent()) {
            return new Answer<>(host.get().readRequest(reviewRequestUrl), TokenUsage.NONE);
        }
        return assistant.readMergeRequest(reviewRequestUrl);
    }

    /** Attributes what a read cost to the task it named, once that task exists. */
    public void charge(String taskId, TokenUsage usage) {
        assistant.chargeTask(taskId, usage);
    }

    private Optional<CodeHost> claiming(String reviewRequestUrl) {
        Optional<CodeHost> host = codeHosts.stream().filter(h -> h.supports(reviewRequestUrl)).findFirst();
        host.ifPresent(claimed -> log.debug("Reading {} over the {} API (no tokens spent)", reviewRequestUrl,
                claimed.displayName()));
        return host;
    }
}
