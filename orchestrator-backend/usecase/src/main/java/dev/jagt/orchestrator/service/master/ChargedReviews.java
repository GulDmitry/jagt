package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.TrustedGitState;
import dev.jagt.orchestrator.service.UsageTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** One Master read, its spend charged to the task it read for, made only while its git is trusted. */
@Service
@RequiredArgsConstructor
public class ChargedReviews {

    private final RoundReviewer reviewer;
    private final UsageTracker usage;
    private final TrustedGitState trusted;

    public Judgement review(String taskId, RoundReviewer.Round round) {
        Optional<String> distrusted = trusted.distrust(taskId);
        if (distrusted.isPresent()) {
            return Judgement.failed(distrusted.get());
        }
        var read = reviewer.review(round);
        usage.chargeTask(taskId, read.usage());
        return read.facts().orElse(Judgement.failed("the review answered nothing"));
    }

    public boolean loadsMcpServer() {
        return reviewer.loadsMcpServer();
    }
}
