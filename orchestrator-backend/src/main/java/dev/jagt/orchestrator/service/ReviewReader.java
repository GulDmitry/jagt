package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.codehost.CodeHost;
import dev.jagt.orchestrator.model.ReviewFacts;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Where a review round comes from: a configured {@link CodeHost}'s REST API when it can read that URL (free,
 * instant), otherwise the metered headless assistant (a full model call, the dominant per-task cost).
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
        Optional<CodeHost> host = codeHosts.stream().filter(h -> h.supports(reviewRequestUrl)).findFirst();
        if (host.isPresent()) {
            log.debug("Reading the review of {} over {} REST (no tokens spent)", reviewRequestUrl,
                    host.get().displayName());
            return host.get().readReview(reviewRequestUrl);
        }
        var answer = assistant.readReview(reviewRequestUrl);
        // Charged even when the read came back empty: the call was paid for either way, and the poll repeats
        // up to hourly for a day, so an uncharged failure would understate what the task actually costs.
        assistant.chargeTask(taskId, answer.usage());
        return answer.facts();
    }
}
