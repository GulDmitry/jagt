package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;

/** Reads a review request by URL on any code host. Every call is booked as it returns. */
public interface CodeHostAssistant {

    Answer<MergeRequestFacts> readMergeRequest(String mrUrl);

    /** Checks state plus the threads of a review request still awaiting an answer; a slow, multi-call read. */
    Answer<ReviewFacts> readReview(String mrUrl);
}
