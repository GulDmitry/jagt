package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.assistant.MasterAssistant.TicketFacts;
import dev.jagt.orchestrator.model.LaunchRequest;
import dev.jagt.orchestrator.model.NewTask;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Starts a task from a ticket reference or a reopened review request — the one place that does, so the console
 * and the web board create tasks identically. It owns the decisions launching needs (does the branch already
 * exist, must the ticket be read at all, which project) and nothing about how the request arrived: parsing a
 * command line stays in the console, parsing JSON stays in the controller.
 */
@Service
public class TaskLauncher {

    /** A bare issue key like {@code ABC-123} — used only to skip the read on the fast path, never parsed out of a URL. */
    private static final Pattern KEY_REF = Pattern.compile("[A-Za-z][A-Za-z0-9]*-[0-9]+");

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TaskLauncher.class);

    private final TaskProvisioning provisioning;
    private final MeteredAssistant assistant;
    private final ConfigService configService;
    private final TaskResume resumes;
    private final TicketTitleBackfill titles;

    public TaskLauncher(TaskProvisioning provisioning, MeteredAssistant assistant, ConfigService configService,
                        TaskResume resumes, TicketTitleBackfill titles) {
        this.provisioning = provisioning;
        this.assistant = assistant;
        this.configService = configService;
        this.resumes = resumes;
        this.titles = titles;
    }

    /**
     * Spins up a task for {@code ref} — an issue key or a URL to it in any tracker. Returns the message to
     * show the human; throws {@link IllegalArgumentException} when the request itself is unusable (unknown
     * project, ambiguous labels).
     */
    public String launch(LaunchRequest request) {
        String ref = request.ref();
        String project = request.project();
        String strategy = request.strategy();
        boolean bareKey = KEY_REF.matcher(ref).matches();

        // Warn before spending a ticket read on a task that would only collide later; a chosen strategy
        // means the collision is intended, so let it through.
        if (bareKey && strategy == null) {
            String existing = provisioning.existingBranchProject(ref, project);
            if (existing != null) {
                return "branch '" + ref + "' already exists in " + existing + " (previous run of this"
                        + " ticket). Retry with `do " + ref + " recreate` (discard old work, start fresh)"
                        + " or `do " + ref + " resume` (continue its commits).";
            }
        }

        // Fast path: a bare key + explicit project needs no read — the key IS the task id.
        if (bareKey && project != null) {
            String result = provisioning.initializeTask(newTask(ref, resolveProject(project),
                    readAndImplement(ref, request), request).build());
            titles.of(ref);
            return result;
        }
        // Otherwise read the item. `ref` may be a KEY or a URL to any tracker — the assistant follows it
        // and returns the canonical key (jagt names the branch/worktree by it; it is NOT parsed from a URL).
        var read = assistant.readTicket(ref);       // the session total is booked by the meter itself
        var facts = read.facts();
        if (facts.isPresent() && !facts.get().exists()) {
            return "error: could not read " + ref + " (bad/inaccessible URL or unknown key?)";
        }
        if (facts.isPresent()) {
            TicketFacts f = facts.get();
            // Name the task by the canonical key the assistant read back; if it returned none but the
            // caller already gave a bare key, that key is a fine task id (a URL has no such fallback).
            String taskId = f.key() != null && !f.key().isBlank() ? f.key() : (bareKey ? ref : null);
            if (taskId == null) {
                return "error: read " + ref + " but the assistant returned no issue key to name the task";
            }
            String resolved = project != null ? resolveProject(project) : resolveByLabels(f);
            String instructions = withNotes("Implement " + taskId + " — \"" + f.title()
                    + "\". Read it via your issue-tracker MCP for full details, then work.", request.notes());
            String result = provisioning.initializeTask(newTask(taskId, resolved, instructions, request)
                    .title(f.title()).ticketUrl(f.url()).build());
            // Only NOW does the task exist, so only now can the read that named it be charged to it —
            // charging earlier silently dropped the most expensive call in a task's life.
            assistant.chargeTask(taskId, read.usage());
            return result;
        }
        // Assistant unavailable: only a bare key can proceed — a URL has no derivable task id without it.
        if (!bareKey) {
            return "error: assistant unavailable — pass an issue key (not a URL), or add the project";
        }
        String result = provisioning.initializeTask(newTask(ref, resolveProject(project),
                readAndImplement(ref, request), request).build());
        // The read FAILED but was still paid for, and the key alone was enough to create the task — so the
        // one case where money bought nothing must not be the one case the task reports as free.
        assistant.chargeTask(ref, read.usage());
        return result;
    }

    /**
     * Reopened review request: the URL is the ONLY input, because the request already carries every answer —
     * its SOURCE branch is the task (a jagt task IS its branch) and its TARGET is the base that the next ship
     * must update rather than open a second request against. A ticket is NOT accepted here: when it disagrees
     * with the source branch, `ship` pushes one branch and updates the request of another.
     */
    public String resume(String reviewRequestUrl) {
        var read = assistant.readMergeRequest(reviewRequestUrl);
        var request = read.facts();
        if (request.isEmpty() || !request.get().exists()) {
            return "error: could not read the review request (or not found): " + reviewRequestUrl;
        }
        // The read also carries the title, so a resumed task shows one on the board just like a `do` task.
        String taskId = request.get().sourceBranch();
        if (taskId == null || taskId.isBlank()) {
            return "error: the review request names no source branch: " + reviewRequestUrl;
        }
        // Someone else's branch is not bound by jagt's naming, and a task IS its branch — so say which branch
        // and why, instead of letting the generic id check report a regex the human never typed.
        if (!TaskProvisioning.isSafeId(taskId)) {
            return "error: source branch '" + taskId + "' cannot be a jagt task — a task IS its branch, and"
                    + " that name also becomes a directory and a tmux window (allowed: letters, digits, '-',"
                    + " '_'). Work in the branch directly, or start a task of your own with `do <ticket> from "
                    + taskId + "`.";
        }
        String result = resumes.resume(taskId, reviewRequestUrl, request.get().title(),
                request.get().targetBranch());
        assistant.chargeTask(taskId, read.usage());       // the task exists only after resumeTask
        return result;
    }

    /** Picks the jagt project whose configured labels intersect the ticket's labels (or tracker project key). */
    private String resolveByLabels(TicketFacts facts) {
        Map<String, List<String>> projectLabels = new LinkedHashMap<>();
        configService.load().projects().forEach((key, project) -> projectLabels.put(key, project.labels()));
        List<String> matches = projectsMatching(facts, projectLabels);
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("no project matches ticket labels " + facts.labels()
                    + " — specify the project");
        }
        throw new IllegalArgumentException("ticket labels match multiple projects " + matches
                + " — specify the project");
    }

    /** Pure: the project keys whose labels intersect the ticket's labels or its tracker project key. */
    public static List<String> projectsMatching(TicketFacts facts, Map<String, List<String>> projectLabels) {
        Set<String> tokens = new HashSet<>(facts.labels());
        tokens.add(facts.trackerProject());
        return projectLabels.entrySet().stream()
                .filter(entry -> entry.getValue() != null
                        && entry.getValue().stream().anyMatch(tokens::contains))
                .map(Map.Entry::getKey)
                .toList();
    }

    /** The configured project to use: the named one, or the only one there is. */
    public String resolveProject(String project) {
        Set<String> keys = configService.load().projects().keySet();
        if (project != null && !project.isBlank()) {
            if (!keys.contains(project)) {
                throw new IllegalArgumentException("unknown project '" + project + "'. Configured: " + keys);
            }
            return project;
        }
        if (keys.size() == 1) {
            return keys.iterator().next();
        }
        throw new IllegalArgumentException("multiple projects " + keys + " — specify one");
    }

    /** The modifiers that come from the human, threaded into every creation path from one place. */
    private static NewTask.Builder newTask(String taskId, String projectKey, String instructions,
                                           LaunchRequest request) {
        return NewTask.builder(taskId, projectKey)
                .instructions(instructions)
                .mode(request.mode())
                .branchStrategy(request.strategy())
                .baseBranch(request.baseBranch());
    }

    /** The brief for a task jagt could not read the ticket for: the agent reads it itself. */

    private static String readAndImplement(String ref, LaunchRequest request) {
        return withNotes("Read " + ref + " via your issue-tracker MCP and implement it.", request.notes());
    }

    private static String withNotes(String instructions, String notes) {
        return notes == null || notes.isBlank()
                ? instructions
                : instructions + "\n\nAdditional instructions from the human:\n" + notes;
    }
}
