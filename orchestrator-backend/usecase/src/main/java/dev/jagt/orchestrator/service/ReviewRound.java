package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.task.ReviewFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** A request's round: the model's read of it, its note ids replaced by the host's own. */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewRound {

    private final ReviewReader reader;
    private final PipelineRetry host;

    public Answer<ReviewFacts> read(String taskId, String project, String reviewRequestUrl) {
        Answer<ReviewFacts> answer = reader.read(taskId, reviewRequestUrl);
        Optional<ReviewFacts> read = answer.facts();
        if (read.isEmpty() || !read.get().request().exists()) {
            return answer;
        }
        Answer<List<String>> notes = host.unresolvedNotes(project, reviewRequestUrl);
        if (notes.facts().isEmpty()) {
            log.atWarn().setMessage("note ids not read")
                    .addKeyValue("ref", reviewRequestUrl)
                    .addKeyValue("cause", notes.failure())
                    .log();
        }
        return new Answer<>(Optional.of(read.get().withNewestNotes(notes.facts().orElse(List.of()))), answer.usage(),
                answer.failure(), answer.refused(), answer.reach());
    }
}
