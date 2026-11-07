package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.With;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * What a task left behind after `done` removed it. The status log is kept whole rather than summarised: whatever
 * is later asked of finished work — how long a phase took, how many rounds it cost — is computable from it, and a
 * number this record did not think to store cannot be recovered.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FinishedTask(
        @JsonUnwrapped Identity identity,
        long finishedAt,
        @JsonUnwrapped Spend spend,
        @JsonUnwrapped Course course,
        // The session logs the agent spend was read from: paths only, the logs themselves are not kept.
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> transcripts
) {

    @With
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Identity(String id, @Nullable String alias, @Nullable String title, List<String> projects,
                           @Nullable String ticketUrl) {
    }

    @With
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Spend(
            long tokens,
            // List price of the agent session plus jagt's own reads of this task, or null where the session's spend was never read.
            @Nullable Double costUsd,
            // The task's own reads and the agent sessions' spend apart, null where nothing was counted.
            @Nullable TokenUsage usage,
            @Nullable TokenUsage agentUsage) {

        /** Whether a call of an unpriced model is missing from {@code costUsd}. */
        @JsonIgnore
        public boolean costIsLowerBound() {
            return costUsd != null && ((usage != null && usage.costIsLowerBound())
                    || (agentUsage != null && agentUsage.costIsLowerBound()));
        }
    }

    @With
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Course(
            List<StatusChange> history,
            int rounds,
            // The host's last word on the checks, or null if nothing ever read one.
            @Nullable String checks,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<Guard, Integer> refusals,
            // The stretches the human held the turn: when, until when, and why.
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<HumanWait> waits,
            // What the unattended reviewer concluded last, or null where nothing judged this task.
            @Nullable String masterVerdict) {

        public Course {
            refusals = refusals == null ? Map.of() : Map.copyOf(refusals);
            waits = waits == null ? List.of() : List.copyOf(waits);
        }
    }

    public FinishedTask {
        transcripts = transcripts == null ? List.of() : List.copyOf(transcripts);
    }

    public static FinishedTask of(String id, TaskState task, long finishedAt) {
        return of(id, task, finishedAt, null);
    }

    public static FinishedTask of(String id, TaskState task, long finishedAt, @Nullable String masterVerdict) {
        return new FinishedTask(
                new Identity(id, task.alias(), task.title(), task.projects(), task.ticketUrl()), finishedAt,
                new Spend(task.totalUsage().total(),
                        task.agentSpendOrNone().usageOrNone().isNone() ? null : task.totalUsage().costUsd(),
                        countedOrNull(task.usageOrNone()), countedOrNull(task.agentSpendOrNone().usageOrNone())),
                new Course(task.history(), task.rounds(), task.checksRead(), task.refusals(),
                        HumanWait.endedAt(task.waits(), finishedAt), masterVerdict),
                List.copyOf(task.agentSpendOrNone().marks().keySet()));
    }

    private static @Nullable TokenUsage countedOrNull(TokenUsage usage) {
        return usage.isNone() ? null : usage;
    }

    /** Whether the reviewer read this task at all; every comparison below is over these and no others. */
    public boolean judged() {
        return course.masterVerdict() != null && !course.masterVerdict().isBlank();
    }

    /** The one word that means it passed; every other ending was a round going back. */
    public boolean passed() {
        return "ready".equalsIgnoreCase(course.masterVerdict() == null ? "" : course.masterVerdict().strip());
    }

    /** A question judged nothing, so whatever you did after it cannot disagree with it. */
    public boolean asked() {
        return "question".equalsIgnoreCase(course.masterVerdict() == null ? "" : course.masterVerdict().strip());
    }

    /** Who opened it, which is who chose its repository; null for a task opened before origins were stamped. */
    public @Nullable ActionOrigin openedBy() {
        return course.history().isEmpty() ? null : course.history().get(0).origin();
    }

    /** Whether the last round judged came back before the task first reached {@code status}. */
    public boolean judgedBefore(TaskStatus status) {
        int judged = -1;
        int reachedAt = -1;
        for (int i = 0; i < course.history().size(); i++) {
            TaskStatus step = course.history().get(i).status();
            if (step == TaskStatus.REVIEW_PENDING) {
                judged = i;
            }
            if (step == status && reachedAt < 0) {
                reachedAt = i;
            }
        }
        return reachedAt >= 0 && judged < reachedAt;
    }

    /** Whether the task ever stood in this status, which is how a claim about it is checked against what it did. */
    public boolean reached(TaskStatus status) {
        return course.history().stream().anyMatch(step -> step.status() == status);
    }

    /**
     * Whether where this task was done can be LEARNED from. A human chose the repository, or the work reached
     * the shared branch from it — a routing nothing confirmed is the router's own guess coming back as a lesson.
     */
    public boolean routingWorthLearningFrom() {
        return identity.projects().size() == 1
                && (openedBy() != ActionOrigin.TRACKER || reached(TaskStatus.DEPLOYED));
    }

    /** From the first step it took to the moment it was retired; zero where nothing was ever recorded. */
    public long tookMillis() {
        return course.history().isEmpty() ? 0 : finishedAt - course.history().get(0).at();
    }
}
