package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.codehost.CodeHost;
import dev.jagt.orchestrator.model.MergeRequestRef;
import dev.jagt.orchestrator.model.MergeRequestSpec;
import dev.jagt.orchestrator.model.ReviewFacts;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The code host of an end-to-end run: it records the one write a {@link CodeHost} may do and answers the
 * review round the case under test is built on. Parsing a real host's REST answers is that host's own unit
 * test; what a FLOW needs is a request that exists after a ship and reads back afterwards.
 */
class FakeCodeHost implements CodeHost {

    private static final ReviewFacts NOT_FINISHED = new ReviewFacts(true, false, "running", List.of());

    private final String requestUrl;
    /** Written by the test, read by whichever request thread serves the verb it then drives. */
    private final List<MergeRequestSpec> writes = new CopyOnWriteArrayList<>();
    private volatile ReviewFacts round = NOT_FINISHED;

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

    List<MergeRequestSpec> writes() {
        return List.copyOf(writes);
    }

    void forgetEverything() {
        writes.clear();
        round = NOT_FINISHED;
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
