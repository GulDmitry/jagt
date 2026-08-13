package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.assistant.MasterAssistant.TicketFacts;
import dev.jagt.orchestrator.mcp.OrchestratorTools;
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

    private final OrchestratorTools tools;
    private final MeteredAssistant assistant;
    private final ConfigService configService;
    private final StateService stateService;

    public TaskLauncher(OrchestratorTools tools, MeteredAssistant assistant, ConfigService configService,
                        StateService stateService) {
        this.tools = tools;
        this.assistant = assistant;
        this.configService = configService;
        this.stateService = stateService;
    }

    /**
     * The task cap is ENFORCED in {@code TaskProvisioning} — this is the same check run early, because
     * launching reads the ticket first and a read is a paid model call. Refusing after paying for it would
     * charge the human for a task jagt was never going to create.
     */
    private void requireSlot(String ref) {
        TaskAdmission.requireSlot(ref, configService.load().agent().maxConcurrentTasksOrDefault(),
                stateService.tasks());
    }

    /**
     * Spins up a task for {@code ref} — an issue key or a URL to it in any tracker. Returns the message to
     * show the human; throws {@link IllegalArgumentException} when the request itself is unusable (unknown
     * project, ambiguous labels).
     */
    public String launch(String ref, String project, String mode, String strategy, String notes) {
        requireSlot(ref);
        boolean bareKey = KEY_REF.matcher(ref).matches();

        // Warn before spending a ticket read on a task that would only collide later; a chosen strategy
        // means the collision is intended, so let it through.
        if (bareKey && strategy == null) {
            String existing = tools.existingBranchProject(ref, project);
            if (existing != null) {
                return "branch '" + ref + "' already exists in " + existing + " (previous run of this"
                        + " ticket). Retry with `do " + ref + " recreate` (discard old work, start fresh)"
                        + " or `do " + ref + " resume` (continue its commits).";
            }
        }

        // Fast path: a bare key + explicit project needs no read — the key IS the task id.
        if (bareKey && project != null) {
            return tools.initializeTask(ref, resolveProject(project),
                    withNotes("Read " + ref + " via your issue-tracker MCP and implement it.", notes),
                    mode, strategy, null, null);
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
                    + "\". Read it via your issue-tracker MCP for full details, then work.", notes);
            String result = tools.initializeTask(taskId, resolved, instructions, mode, strategy,
                    f.title(), f.url());
            // Only NOW does the task exist, so only now can the read that named it be charged to it —
            // charging earlier silently dropped the most expensive call in a task's life.
            assistant.chargeTask(taskId, read.usage());
            return result;
        }
        // Assistant unavailable: only a bare key can proceed — a URL has no derivable task id without it.
        if (!bareKey) {
            return "error: assistant unavailable — pass an issue key (not a URL), or add the project";
        }
        String result = tools.initializeTask(ref, resolveProject(project),
                withNotes("Read " + ref + " via your issue-tracker MCP and implement it.", notes),
                mode, strategy, null, null);
        // The read FAILED but was still paid for, and the key alone was enough to create the task — so the
        // one case where money bought nothing must not be the one case the task reports as free.
        assistant.chargeTask(ref, read.usage());
        return result;
    }

    /**
     * Reopened review request: the URL is enough. The assistant reads it for the source branch (= the task)
     * and its project; jagt resumes that branch and links the request at CI_POLLING (no new request). An
     * explicit ticket may be given to skip the lookup.
     */
    public String resume(String reviewRequestUrl, String ticket) {
        requireSlot(ticket == null ? reviewRequestUrl : ticket);
        // The read also carries the title, so a resumed task shows one on the board just like a `do` task.
        String title = null;
        var read = assistant.readMergeRequest(reviewRequestUrl);
        var request = read.facts();
        if (request.isPresent() && request.get().exists()) {
            title = request.get().title();
            if (ticket == null) {
                ticket = request.get().sourceBranch();
            }
        } else if (ticket == null) {
            return "error: could not read the review request (or not found): " + reviewRequestUrl;
        }
        String result = tools.resumeTask(ticket, reviewRequestUrl, title);
        assistant.chargeTask(ticket, read.usage());       // the task exists only after resumeTask
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

    private static String withNotes(String instructions, String notes) {
        return notes == null || notes.isBlank()
                ? instructions
                : instructions + "\n\nAdditional instructions from the human:\n" + notes;
    }
}
