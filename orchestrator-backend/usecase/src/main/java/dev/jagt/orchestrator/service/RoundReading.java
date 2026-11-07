package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.service.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Every request of a task read into ONE round, or the reason there is no round to judge. */
@Service
@RequiredArgsConstructor
public class RoundReading {

    public sealed interface Read permits Refused, Round {
    }

    /** {@code request} is the address of the one request the refusal is about, blank where it is about none. */
    public record Refused(SweepResult result, String request) implements Read {

        public Refused(SweepResult result) {
            this(result, "");
        }
    }

    /** {@code project} is the repository whose pipeline the round's checks and failed jobs are. */
    public record Round(String project, String mrUrl, ReviewFacts facts, String tip) implements Read {
    }

    private static final int CAUSE_LIMIT = 200;

    private final StateService stateService;
    private final ReviewRound reviewRound;
    private final MergedRequests mergedRequests;

    /** A repository with no request is work nobody is reviewing, so the round cannot be called clean. */
    private Optional<Refused> unshipped(String taskId, List<TaskRepo> repos) {
        List<String> unshipped = mergedRequests.owingARequest(repos);
        return unshipped.isEmpty() ? Optional.empty()
                : Optional.of(new Refused(new SweepResult(SweepResult.Kind.PENDING, "sweep " + taskId + ": no request in "
                        + String.join(", ", unshipped) + " — `ship` again")));
    }

    public Read read(String taskId) {
        mergedRequests.refresh(taskId);
        List<TaskRepo> repos = stateService.task(taskId).map(TaskState::repos).orElse(List.of());
        List<TaskRepo> reviewed = repos.stream().filter(TaskRepo::hasReviewRequest).toList();
        if (reviewed.isEmpty()) {
            return noRequest(taskId);
        }
        Optional<Refused> unshipped = unshipped(taskId, repos);
        if (unshipped.isPresent()) {
            return unshipped.get();
        }
        // One unreadable request fails the WHOLE sweep: half a task's repositories cannot say "green".
        // Taken before the host is asked: a push between the two leaves the read under the older commit, which only hides it.
        String tip = mergedRequests.pushedTips(taskId);
        List<TaskRepo> kept = new ArrayList<>();
        List<ReviewFacts> rounds = new ArrayList<>();
        for (TaskRepo repo : reviewed) {
            Answer<ReviewFacts> answer = reviewRound.read(taskId, Objects.requireNonNull(repo.project()), Objects.requireNonNull(repo.mrUrl()));
            Optional<ReviewFacts> read = answer.facts();
            if (read.isEmpty()) {
                return new Refused(new SweepResult(SweepResult.Kind.UNREADABLE,
                        "error: read failed: " + repo.mrUrl() + " " + cause(answer.failure())));
            }
            if (leaves(taskId, repo, read.get())) {
                continue;
            }
            if (!standing(read.get())) {
                return new Refused(new SweepResult(SweepResult.Kind.GONE,
                        "error: no such request: " + repo.mrUrl() + " (the host says so)"), Objects.requireNonNull(repo.mrUrl()));
            }
            if (read.get().request().merged()) {
                mergedRequests.hostSaidMerged(taskId, Objects.requireNonNull(repo.project()));
            }
            kept.add(repo);
            rounds.add(read.get());
        }
        return kept.isEmpty() ? noRequest(taskId) : roundOf(kept, rounds, tip);
    }

    private boolean leaves(String taskId, TaskRepo repo, ReviewFacts read) {
        return read.request().exists() && read.request().closed() && mergedRequests.hostSaidClosed(taskId, repo);
    }

    private static boolean standing(ReviewFacts read) {
        return read.request().exists() && !read.request().closed();
    }

    private static Refused noRequest(String taskId) {
        return new Refused(new SweepResult(SweepResult.Kind.NO_MR, "error: no request linked to " + taskId + " — `ship` first"));
    }

    private static Round roundOf(List<TaskRepo> kept, List<ReviewFacts> read, String tip) {
        List<ReviewFacts> rounds = kept.size() == 1 ? read : IntStream.range(0, kept.size())
                .mapToObj(i -> named(Objects.requireNonNull(kept.get(i).project()), read.get(i))).toList();
        return new Round(checkedProject(kept, rounds), kept.stream().map(TaskRepo::mrUrl).collect(Collectors.joining(", ")), merged(rounds), tip);
    }

    private static String checkedProject(List<TaskRepo> kept, List<ReviewFacts> rounds) {
        return Objects.toString(kept.get(rounds.indexOf(worstChecks(rounds))).project(), "");
    }

    static String cause(String failure) {
        String line = failure.strip().replaceAll("\\s+", " ");
        return line.isEmpty() ? "(cause in the log)" : "— " + (line.length() > CAUSE_LIMIT ? line.substring(0, CAUSE_LIMIT) + "…" : line);
    }

    private static ReviewFacts named(String project, ReviewFacts round) {
        return new ReviewFacts(round.request(),
                round.pipeline().withFailure(round.pipeline().failure().isBlank() ? "" : "[" + project + "] " + round.pipeline().failure()),
                round.discussion()
                        .withThreads(round.discussion().threads().stream().map(thread -> "[" + project + "] " + thread).toList())
                        .withAwaitingReviewer(round.discussion().awaitingReviewer().stream().map(thread -> "[" + project + "] " + thread).toList()));
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
        return new ReviewFacts(
                new ReviewFacts.Request(true, rounds.stream().allMatch(round -> round.request().approved()),
                        rounds.stream().allMatch(round -> round.request().merged()), false, longestOpen(rounds)),
                worst.pipeline(),
                new ReviewFacts.Discussion(
                        rounds.stream().flatMap(round -> round.discussion().threads().stream()).toList(),
                        rounds.stream().flatMap(round -> round.discussion().awaitingReviewer().stream()).toList(),
                        rounds.stream().flatMap(round -> round.discussion().newestNotes().stream()).toList()));
    }

    /** The OLDEST request: how long the review has been hanging is the longest any of them has waited. */
    private static long longestOpen(List<ReviewFacts> rounds) {
        return rounds.stream().mapToLong(round -> round.request().openedAt()).filter(opened -> opened > 0).min().orElse(0);
    }

    /**
     * The worst repository's round, ordered by VERDICT rather than by the words: the word the task carries and the
     * failure the agent reads must come off the SAME repository, or the brief quotes a log from a green one.
     */
    private static ReviewFacts worstChecks(List<ReviewFacts> rounds) {
        return rounds.stream()
                .min(Comparator.comparingInt(round -> Pipeline.of(round.pipeline().status()).severity()))
                .orElseThrow();
    }
}
