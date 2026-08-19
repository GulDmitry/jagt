package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.port.CodeHost;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.MergeRequestRef;
import dev.jagt.orchestrator.task.MergeRequestSpec;
import dev.jagt.orchestrator.task.ReviewFacts;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The code host of an end-to-end run: it records the one write a {@link CodeHost} may do and answers what the
 * case under test reads back — the review round, and the request a task is re-entered on. Parsing a real host's
 * REST answers is that host's own unit test; what a FLOW needs is a request that exists after a ship and reads
 * back afterwards.
 */
class FakeCodeHost implements CodeHost {

    private static final ReviewFacts NOT_FINISHED = new ReviewFacts(true, false, "running", List.of());

    private final String requestUrl;
    /** Written by the test, read by whichever request thread serves the verb it then drives. */
    private final List<MergeRequestSpec> writes = new CopyOnWriteArrayList<>();
    private volatile ReviewFacts round = NOT_FINISHED;
    private volatile MergeRequestFacts request;

    FakeCodeHost(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    String requestUrl() {
        return requestUrl;
    }

    /** Null answers that the request cannot be read at all, which no round may be mistaken for. */
    void answers(ReviewFacts round) {
        this.round = round;
    }

    void answers(MergeRequestFacts request) {
        this.request = request;
    }

    List<MergeRequestSpec> writes() {
        return List.copyOf(writes);
    }

    void forgetEverything() {
        writes.clear();
        round = NOT_FINISHED;
        request = null;
    }

    @Override
    public String displayName() {
        return "Fake";
    }

    @Override
    public boolean supports(String reviewRequestUrl) {
        return requestUrl.equals(reviewRequestUrl);
    }

    @Override
    public Optional<ReviewFacts> readReview(String reviewRequestUrl) {
        return supports(reviewRequestUrl) ? Optional.ofNullable(round) : Optional.empty();
    }

    @Override
    public Optional<MergeRequestFacts> readRequest(String reviewRequestUrl) {
        return supports(reviewRequestUrl) ? Optional.ofNullable(request) : Optional.empty();
    }

    /** A throwaway origin is a local directory, so neither side has a host part to match on. */
    @Override
    public boolean hostsRepository(String gitRemoteUrl) {
        return gitRemoteUrl != null && !gitRemoteUrl.isBlank();
    }

    @Override
    public Optional<MergeRequestRef> createOrUpdateMergeRequest(MergeRequestSpec spec) {
        writes.add(spec);
        return Optional.of(new MergeRequestRef(requestUrl, writes.size() == 1));
    }
}
