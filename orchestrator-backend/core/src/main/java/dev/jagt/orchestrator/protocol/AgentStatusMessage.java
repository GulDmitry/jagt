package dev.jagt.orchestrator.protocol;

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
public record AgentStatusMessage(String status, String message, String outcome, String reviewRequestUrl,
                                 Map<String, String> reviewRequests) {

    /** The wire spelling of what a report says about a round; absent falls back to a marker in the message. */
    public static final Set<String> OUTCOMES = Set.of("progress", "question", "no_changes");

    private static final Pattern URL = Pattern.compile("https?://\\S+");

    public AgentStatusMessage {
        reviewRequests = reviewRequests == null ? Map.of() : Map.copyOf(reviewRequests);
    }

    /**
     * Every violation at once, field rules and consistency rules together: a sender told about one error at a
     * time fixes them one call at a time. {@code projectsOnTask} empty means the task is unknown, which is a
     * different refusal and not this one's to make.
     */
    public List<Violation> violations(List<String> projectsOnTask) {
        List<Violation> found = new ArrayList<>();
        if (parsedStatus() == null) {
            found.add(new Violation("status", "one of " + List.of(TaskStatus.values())));
        }
        if (outcome != null && !outcome.isBlank() && !OUTCOMES.contains(normalised(outcome))) {
            found.add(new Violation("outcome", "one of " + OUTCOMES + ", or left out"));
        }
        if (!reviewRequests.isEmpty() && reviewRequestUrl != null && !reviewRequestUrl.isBlank()) {
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
    public Optional<Reported> accepted(List<String> projectsOnTask) {
        if (!violations(projectsOnTask).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Reported(parsedStatus(), claimed(), detail(), link(projectsOnTask),
                reviewRequests));
    }

    /** What this report says about a round: the typed field first, the marker the message opens with second. */
    private AgentReport claimed() {
        if (outcome == null || outcome.isBlank()) {
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
