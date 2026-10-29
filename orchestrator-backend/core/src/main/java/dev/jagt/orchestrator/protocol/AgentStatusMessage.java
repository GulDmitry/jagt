package dev.jagt.orchestrator.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.task.TaskStatus;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static dev.jagt.orchestrator.protocol.Wire.absent;

/**
 * What a session says about its own task, as it arrives on the wire. Raw strings: a value out of its enum is a
 * VIOLATION to report back, not an exception on the way in.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentStatusMessage(@Nullable String status, @Nullable String message, @Nullable String outcome,
                                 @Nullable String reviewRequestUrl, Map<String, String> reviewRequests,
                                 @Nullable String taskId, @Nullable String needsProject,
                                 @Nullable String why, Map<String, String> switches) implements Message {

    /** The wire spelling of what a report means; absent is progress. */
    public static final Set<String> OUTCOMES = Set.of("progress", "question", "permission", "no_changes",
            "no_plan", "wait");

    private static final Pattern URL = Pattern.compile("https?://\\S+");

    private static final List<TaskStatus> OFFERED = java.util.Arrays.stream(TaskStatus.values())
            .filter(FlowRules::reportable).toList();

    /** The form a caller is given, beside the rules it is judged by: one declaration, never two that drift. */
    public static final Schema SCHEMA = Schema.of(
                    "Report the task's status to jagt. Sub-agents call this when their status changes, and"
                            + " MUST call it with outcome=question BEFORE"
                            + " putting any question to the human — an interactive choice in your own window"
                            + " reaches nobody, and this call is the only thing that puts the question on their"
                            + " board. taskId defaults to the calling worktree's task.")
            .choiceRequired("status", OFFERED, null)
            .choice("outcome", OUTCOMES,
                    "What this report says about the work, in jagt's own words rather than yours:"
                            + " `question` = you have STOPPED and need the human (the message is the question),"
                            + " `permission` = the same, stopped on a call your CLI denied: only the human grants"
                            + " it (the message names the call),"
                            + " `no_changes` = a review round edited no code (all comments already handled, or"
                            + " you pushed back on every one), `progress` = anything else. Checked where it can"
                            + " be: report no_changes over an edited worktree and it is recorded as a round with"
                            + " a diff, `no_plan` = autoplan only: with status IN_PROGRESS, the task meets none of"
                            + " its criteria, so no plan comes first (the message is the reason),"
                            + " `wait` = with status IN_PROGRESS: a dependency you need is down and nothing here can"
                            + " bring it up (the message names it and how to probe it).")
            .text("reviewRequestUrl", "The review request this report is about. Required with CI_POLLING (jagt"
                    + " links it, and the board is where the human follows it).")
            .pairs("reviewRequests", "One request URL per project key, for a task spanning several repositories:"
                            + " {\"<project>\": \"<url>\"}. Give it instead of reviewRequestUrl and name EVERY"
                            + " repository you opened one in — it is still one round.",
                    List.of(Map.of("api", "https://host/api/-/merge_requests/7",
                            "web", "https://host/web/-/merge_requests/3")))
            .text("message", "For the HUMAN, 10 words MAX — it renders as one narrow dashboard table line"
                    + " (longer text is truncated). No marker words: what the report MEANS is the outcome field.")
            .text("needsProject", "With outcome=question only: the key of the configured project (`list_projects`)"
                    + " whose repository this task needs and lacks. jagt adds it to the task on a human's or the"
                    + " Master's word; the message says why.")
            .text("taskId", "Optional explicit task id or alias (Master use). Sub-agents may only target their"
                    + " own task.")
            .pairs(SwitchJudgements.FIELD, SwitchJudgements.DESCRIBES,
                    List.of(Map.of("visualize", "no — config-only change, nothing to draw")))
            .required(Why.FIELD, "string", Why.DESCRIBES);

    public AgentStatusMessage {
        // Blank is absent: a field the sender left as "" must read the same as one it left out.
        status = absent(status);
        message = absent(message);
        outcome = absent(outcome);
        reviewRequestUrl = absent(reviewRequestUrl);
        taskId = absent(taskId);
        needsProject = absent(needsProject);
        why = absent(why);
        reviewRequests = reviewRequests == null ? Map.of() : Map.copyOf(reviewRequests);
        switches = switches == null ? Map.of() : Map.copyOf(switches);
    }

    /** The five-field form, for jagt's own reports, which name no task of someone else's. */
    public AgentStatusMessage(@Nullable String status, @Nullable String message, @Nullable String outcome,
                              @Nullable String reviewRequestUrl, Map<String, String> reviewRequests) {
        this(status, message, outcome, reviewRequestUrl, reviewRequests, null, null, null, Map.of());
    }

    public static AgentStatusMessage of(@Nullable String status, @Nullable String message) {
        return new AgentStatusMessage(status, message, null, null, Map.of(), null, null, null, Map.of());
    }

    public AgentStatusMessage withOutcome(@Nullable String outcome) {
        return new AgentStatusMessage(status, message, outcome, reviewRequestUrl, reviewRequests, taskId, needsProject, why, switches);
    }

    public AgentStatusMessage withReviewRequestUrl(@Nullable String reviewRequestUrl) {
        return new AgentStatusMessage(status, message, outcome, reviewRequestUrl, reviewRequests, taskId, needsProject, why, switches);
    }

    public AgentStatusMessage withReviewRequests(Map<String, String> reviewRequests) {
        return new AgentStatusMessage(status, message, outcome, reviewRequestUrl, reviewRequests, taskId, needsProject, why, switches);
    }

    /**
     * Every violation at once, field rules and consistency rules together: a sender told about one error at a
     * time fixes them one call at a time.
     */
    @Override
    public List<Violation> violations(MessageContext context) {
        List<Violation> found = new ArrayList<>();
        Why.violation(why).ifPresent(found::add);
        found.addAll(SwitchJudgements.violations(switches, context.switchesOwed()));
        found.addAll(reportViolations(context));
        return List.copyOf(found);
    }

    /**
     * Every rule but the sender's reason and switch judgements, which a report jagt writes for itself does not give. {@code projectsOnTask}
     * empty means the task is unknown, which is a different refusal and not this one's to make.
     */
    public List<Violation> reportViolations(MessageContext context) {
        List<String> projectsOnTask = context.projectsOnTask();
        List<Violation> found = new ArrayList<>();
        if (status == null) {
            found.add(new Violation("status", "required, one of " + OFFERED));
        } else if (!OFFERED.contains(parsedStatus())) {
            found.add(new Violation("status", "one of " + OFFERED));
        }
        if (outcome != null && !OUTCOMES.contains(normalised(outcome))) {
            found.add(new Violation("outcome", "one of " + OUTCOMES + ", or left out"));
        }
        if (noPlan() && context.planRequired()) {
            found.add(new Violation("outcome", "not no_plan: this task was opened in plan mode, so its plan comes first"));
        }
        if (noPlan() && parsedStatus() != TaskStatus.IN_PROGRESS) {
            found.add(new Violation("status", "IN_PROGRESS with no_plan"));
        }
        waitViolations(context).forEach(found::add);
        if (!reviewRequests.isEmpty() && reviewRequestUrl != null) {
            found.add(new Violation("reviewRequests", "given instead of reviewRequestUrl, never beside it"));
        }
        notWebLinks().forEach(link -> found.add(new Violation("reviewRequestUrl",
                "an http(s) link the human can open, not " + link)));
        if (parsedStatus() == TaskStatus.CI_POLLING && link(projectsOnTask) == null) {
            found.add(new Violation("reviewRequestUrl",
                    "required with CI_POLLING: the board is where the human follows the round"));
        }
        unknownProjects(projectsOnTask).forEach(key -> found.add(new Violation("reviewRequests",
                "project '" + key + "' is not on this task; its projects are " + projectsOnTask)));
        needsProjectViolation(context).ifPresent(found::add);
        return List.copyOf(found);
    }

    private List<Violation> waitViolations(MessageContext context) {
        if (!waits()) {
            return List.of();
        }
        List<Violation> found = new ArrayList<>();
        if (context.waitsRefused()) {
            found.add(new Violation("outcome", "not wait: jagt waits for nothing here"));
        }
        if (parsedStatus() != TaskStatus.IN_PROGRESS) {
            found.add(new Violation("status", "IN_PROGRESS with wait"));
        }
        if (message == null) {
            found.add(new Violation("message", "required with wait: what is down and how to probe it"));
        }
        return found;
    }

    private Optional<Violation> needsProjectViolation(MessageContext context) {
        if (needsProject == null) {
            return Optional.empty();
        }
        if (outcome == null || !"question".equals(normalised(outcome))) {
            return Optional.of(new Violation("needsProject", "only with outcome=question"));
        }
        if (context.projectsOnTask().contains(needsProject)) {
            return Optional.of(new Violation("needsProject", "project '" + needsProject
                    + "' is already on this task; its projects are " + context.projectsOnTask()));
        }
        if (!context.configuredProjects().isEmpty() && !context.configuredProjects().contains(needsProject)) {
            return Optional.of(new Violation("needsProject", "project '" + needsProject + "' is not configured; the"
                    + " projects are " + context.configuredProjects()));
        }
        return Optional.empty();
    }

    /**
     * The message in jagt's own types, or empty when it broke a rule. {@code projectsOnTask} is the task's
     * repositories, primary first, since which link a human follows first is the session's own repository.
     */
    public Optional<Reported> accepted(MessageContext context) {
        if (!reportViolations(context).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Reported(Objects.requireNonNull(parsedStatus()), claimed(), message,
                link(context.projectsOnTask()), reviewRequests, noPlan(), needsProject,
                SwitchJudgements.normalised(switches)));
    }

    /** Whether the report says the session waits on a dependency that is down. */
    public boolean waits() {
        return outcome != null && "wait".equals(normalised(outcome));
    }

    private boolean noPlan() {
        return outcome != null && "no_plan".equals(normalised(outcome));
    }

    private AgentReport claimed() {
        if (outcome == null) {
            return AgentReport.PLAIN;
        }
        return switch (normalised(outcome)) {
            case "question" -> AgentReport.QUESTION;
            case "permission" -> AgentReport.PERMISSION;
            case "no_changes" -> AgentReport.NO_CHANGES;
            case "wait" -> AgentReport.WAIT;
            default -> AgentReport.PLAIN;
        };
    }

    /** The status this message names, or null when it names none this machine has. */
    public @Nullable TaskStatus parsedStatus() {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TaskStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The request this report is about, wherever it was written: the named field, the map, or the message. */
    private @Nullable String link(List<String> projectsOnTask) {
        if (!reviewRequests.isEmpty()) {
            if (!projectsOnTask.isEmpty() && reviewRequests.containsKey(projectsOnTask.get(0))) {
                return reviewRequests.get(projectsOnTask.get(0));
            }
            return reviewRequests.values().iterator().next();
        }
        if (reviewRequestUrl != null && !reviewRequestUrl.isBlank()) {
            return reviewRequestUrl.strip();
        }
        var matcher = URL.matcher(message == null ? "" : message);
        return matcher.find() ? matcher.group() : null;
    }

    private List<String> notWebLinks() {
        List<String> links = new ArrayList<>(reviewRequests.values());
        if (reviewRequestUrl != null && !reviewRequestUrl.isBlank()) {
            links.add(reviewRequestUrl.strip());
        }
        return links.stream().filter(link -> !link.startsWith("http://") && !link.startsWith("https://")).toList();
    }

    private List<String> unknownProjects(List<String> projectsOnTask) {
        return projectsOnTask.isEmpty() ? List.of()
                : reviewRequests.keySet().stream().filter(key -> !projectsOnTask.contains(key)).toList();
    }

    private static String normalised(String outcome) {
        return outcome.strip().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
