package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.service.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Every request of a task read into ONE round, or the reason there is no round to judge. */
@Service
@RequiredArgsConstructor
public class RoundReading {

    public sealed interface Read permits Refused, Round {
    }

    public record Refused(SweepResult result) implements Read {
    }

    public record Round(String mrUrl, ReviewFacts facts) implements Read {
    }

    private final StateService stateService;
    private final ReviewReader reviewReader;

    public Read read(String taskId) {
        List<TaskRepo> repos = stateService.task(taskId).map(TaskState::repos).orElse(List.of());
        List<TaskRepo> reviewed = repos.stream().filter(TaskRepo::hasReviewRequest).toList();
        if (reviewed.isEmpty()) {
            return new Refused(new SweepResult(SweepResult.Kind.NO_MR,
                    "error: no request linked to " + taskId + " — `ship` first"));
        }
        // A repository with no request is work nobody is reviewing, so the round cannot be called clean.
        List<String> unshipped = repos.stream().filter(repo -> !repo.hasReviewRequest())
                .map(TaskRepo::project).toList();
        if (!unshipped.isEmpty()) {
            return new Refused(new SweepResult(SweepResult.Kind.PENDING, "sweep " + taskId + ": no request in "
                    + String.join(", ", unshipped) + " — `ship` again"));
        }
        // One unreadable request fails the WHOLE sweep: half a task's repositories cannot say "green".
        List<ReviewFacts> rounds = new ArrayList<>();
        for (TaskRepo repo : reviewed) {
            Optional<ReviewFacts> read = reviewReader.read(taskId, repo.mrUrl());
            if (read.isEmpty()) {
                return new Refused(new SweepResult(SweepResult.Kind.UNREADABLE,
                        "error: read failed: " + repo.mrUrl() + " (cause in the log)"));
            }
            if (!read.get().exists()) {
                return new Refused(new SweepResult(SweepResult.Kind.UNREADABLE,
                        "error: no such request: " + repo.mrUrl() + " (the host says so)"));
            }
            rounds.add(reviewed.size() == 1 ? read.get() : named(repo.project(), read.get()));
        }
        return new Round(reviewed.stream().map(TaskRepo::mrUrl).collect(Collectors.joining(", ")), merged(rounds));
    }

    private static ReviewFacts named(String project, ReviewFacts round) {
        return new ReviewFacts(round.exists(), round.approved(), round.pipelineStatus(),
                round.pipelineFailure().isBlank() ? "" : "[" + project + "] " + round.pipelineFailure(),
                round.threads().stream().map(thread -> "[" + project + "] " + thread).toList(),
                round.openedAt());
    }

    /**
     * Several repositories, ONE round: approved only when every request is, and the pipeline reported as the single
     * worst one, a concatenation reading as "success" while one repository still builds.
     */
    private static ReviewFacts merged(List<ReviewFacts> rounds) {
        if (rounds.size() == 1) {
            return rounds.get(0);
        }
        ReviewFacts worst = worstChecks(rounds);
        return new ReviewFacts(true,
                rounds.stream().allMatch(ReviewFacts::approved),
                worst.pipelineStatus(), worst.pipelineFailure(),
                rounds.stream().flatMap(round -> round.threads().stream()).toList(),
                longestOpen(rounds));
    }

    /** The OLDEST request: how long the review has been hanging is the longest any of them has waited. */
    private static long longestOpen(List<ReviewFacts> rounds) {
        return rounds.stream().mapToLong(ReviewFacts::openedAt).filter(opened -> opened > 0).min().orElse(0);
    }

    /**
     * The worst repository's round, ordered by VERDICT rather than by the words: the word the task carries and the
     * failure the agent reads must come off the SAME repository, or the brief quotes a log from a green one.
     */
    private static ReviewFacts worstChecks(List<ReviewFacts> rounds) {
        return rounds.stream()
                .min(Comparator.comparingInt(round -> Pipeline.of(round.pipelineStatus()).severity()))
                .orElseThrow();
    }
}
