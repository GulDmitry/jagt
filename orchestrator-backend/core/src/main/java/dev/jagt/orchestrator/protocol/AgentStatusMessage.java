package dev.jagt.orchestrator.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.flow.TaskStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a session says about its own task, as it arrives on the wire. Raw strings: a value out of its enum is a
 * VIOLATION to report back, not an exception on the way in.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentStatusMessage(String status, String message, String outcome, String reviewRequestUrl,
                                 Map<String, String> reviewRequests, String taskId) implements Message {

    /** The wire spelling of what a report says about a round; absent falls back to a marker in the message. */
    public static final Set<String> OUTCOMES = Set.of("progress", "question", "no_changes");

    private static final Pattern URL = Pattern.compile("https?://\\S+");

    /** The form a caller is given, beside the rules it is judged by: one declaration, never two that drift. */
    public static final Schema SCHEMA = Schema.of(
                    "Update the task status and keep-alive timestamp in state.json. Sub-agents MUST call this"
                            + " frequently to avoid Watchdog alerts, and MUST call it with outcome=question BEFORE"
                            + " putting any question to the human — an interactive choice in your own window"
                            + " reaches nobody, and this call is the only thing that puts the question on their"
                            + " board. taskId defaults to the calling worktree's task.")
            .choiceRequired("status", List.of(TaskStatus.values()), null)
            .choice("outcome", OUTCOMES,
                    "What this report says about the work, in jagt's own words rather than yours:"
                            + " `question` = you have STOPPED and need the human (the message is the question),"
                            + " `no_changes` = a review round edited no code (all comments already handled, or"
                            + " you pushed back on every one), `progress` = anything else. Checked where it can"
                            + " be: report no_changes over an edited worktree and it is recorded as a round with"
                            + " a diff.")
            .text("reviewRequestUrl", "The review request this report is about. Required with CI_POLLING (jagt"
                    + " links it, and the board is where the human follows it).")
            .pairs("reviewRequests", "One request URL per project key, for a task spanning several repositories:"
                            + " {\"<project>\": \"<url>\"}. Give it instead of reviewRequestUrl and name EVERY"
                            + " repository you opened one in — it is still one round.",
                    List.of(Map.of("api", "https://host/api/-/merge_requests/7",
                            "web", "https://host/web/-/merge_requests/3")))
            .text("message", "For the HUMAN, 10 words MAX — it renders as one narrow dashboard table line"
                    + " (longer text is truncated). No marker words: what the report MEANS is the outcome field.")
            .text("taskId", "Optional explicit task id or alias (Master use). Sub-agents may only target their"
                    + " own task.");

    /** Where a link is not in the fields that name one, the message text is read for it. */

    public AgentStatusMessage {
        // Blank is absent: a field the sender left as "" must read the same as one it left out.
        status = absent(status);
        message = absent(message);
        outcome = absent(outcome);
        reviewRequestUrl = absent(reviewRequestUrl);
        taskId = absent(taskId);
        reviewRequests = reviewRequests == null ? Map.of() : Map.copyOf(reviewRequests);
    }

    /** The five-field form, for jagt's own reports, which name no task of someone else's. */
    public AgentStatusMessage(String status, String message, String outcome, String reviewRequestUrl,
                              Map<String, String> reviewRequests) {
        this(status, message, outcome, reviewRequestUrl, reviewRequests, null);
    }

    private static String absent(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Every violation at once, field rules and consistency rules together: a sender told about one error at a
     * time fixes them one call at a time. {@code projectsOnTask} empty means the task is unknown, which is a
     * different refusal and not this one's to make.
     */
    @Override
    public List<Violation> violations(MessageContext context) {
        List<String> projectsOnTask = context.projectsOnTask();
        List<Violation> found = new ArrayList<>();
        if (status == null) {
            found.add(new Violation("status", "required, one of " + List.of(TaskStatus.values())));
        } else if (parsedStatus() == null) {
            found.add(new Violation("status", "one of " + List.of(TaskStatus.values())));
        }
        if (outcome != null && !OUTCOMES.contains(normalised(outcome))) {
            found.add(new Violation("outcome", "one of " + OUTCOMES + ", or left out"));
        }
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
        return List.copyOf(found);
    }

    /**
     * The message in jagt's own types, or empty when it broke a rule. {@code projectsOnTask} is the task's
     * repositories, primary first, since which link a human follows first is the session's own repository.
     */
    public Optional<Reported> accepted(MessageContext context) {
        if (!violations(context).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Reported(parsedStatus(), claimed(), detail(),
                link(context.projectsOnTask()), reviewRequests));
    }

    /** What this report says about a round: the typed field first, the marker the message opens with second. */
    private AgentReport claimed() {
        if (outcome == null) {
            return AgentReport.of(message);
        }
        return switch (normalised(outcome)) {
            case "question" -> AgentReport.QUESTION;
            case "no_changes" -> AgentReport.NO_CHANGES;
            default -> AgentReport.PLAIN;
        };
    }

    /** The human's half of the message, with any marker taken off; null stays null. */
    private String detail() {
        return message == null ? null : AgentReport.withoutMarker(message);
    }

    /** The status this message names, or null when it names none this machine has. */
    public TaskStatus parsedStatus() {
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
    private String link(List<String> projectsOnTask) {
        if (!reviewRequests.isEmpty()) {
            String sessionRepo = projectsOnTask.isEmpty() ? null : projectsOnTask.get(0);
            return reviewRequests.getOrDefault(sessionRepo, reviewRequests.values().iterator().next());
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
