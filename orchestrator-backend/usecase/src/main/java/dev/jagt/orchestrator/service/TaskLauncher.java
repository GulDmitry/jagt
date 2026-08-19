package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.TicketFacts;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The one place a task is started, so every surface creates tasks identically. It owns the decisions launching
 * needs and nothing about how the request arrived: parsing a command line stays in the console, parsing JSON
 * stays in the controller.
 */
@Service
public class TaskLauncher {

    /** A bare issue key like {@code ABC-123} — used only to skip the read on the fast path, never parsed out of a URL. */
    private static final Pattern KEY_REF = Pattern.compile("[A-Za-z][A-Za-z0-9]*-[0-9]+");

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TaskLauncher.class);

    private final TaskProvisioning provisioning;
    private final TicketReader tickets;
    private final ConfigService configService;
    private final TaskResume resumes;
    private final TicketTitleBackfill titles;

    public TaskLauncher(TaskProvisioning provisioning, TicketReader tickets, ConfigService configService,
                        TaskResume resumes, TicketTitleBackfill titles) {
        this.provisioning = provisioning;
        this.tickets = tickets;
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
            String existing = provisioning.existingBranchProject(ref,
                    project == null ? List.of() : resolveProjects(project));
            if (existing != null) {
                return "branch '" + ref + "' already exists in " + existing + " (previous run of this"
                        + " ticket). Retry with `do " + ref + " recreate` (discard old work, start fresh)"
                        + " or `do " + ref + " resume` (continue its commits).";
            }
        }

        // Fast path: a bare key + explicit project needs no read — the key IS the task id.
        if (bareKey && project != null) {
            String result = provisioning.initializeTask(newTask(ref, resolveProjects(project),
                    readAndImplement(ref, request), request).build());
            titles.of(ref);
            return result;
        }
        // The read takes a key or a URL to any tracker and answers with the canonical key, which is what names
        // the branch and the worktree.
        var read = tickets.read(ref);
        var facts = read.facts();
        if (facts.isPresent() && !facts.get().exists()) {
            return "error: could not read " + ref + " (bad/inaccessible URL or unknown key?)";
        }
        if (facts.isPresent()) {
            TicketFacts f = facts.get();
            // A URL has no fallback of its own when the read gave back no key.
            String taskId = f.key() != null && !f.key().isBlank() ? f.key() : (bareKey ? ref : null);
            if (taskId == null) {
                return "error: read " + ref + " but got no issue key back to name the task";
            }
            List<String> resolved = project != null
                    ? resolveProjects(project)
                    : List.of(resolveByLabels(f));
            String instructions = withNotes("Implement " + taskId + " — \"" + f.title()
                    + "\". Read it via your issue-tracker MCP for full details, then work.", request.notes());
            String result = provisioning.initializeTask(newTask(taskId, resolved, instructions, request)
                    .title(f.title()).ticketUrl(f.url()).build());
            // Only NOW does the task exist, so only now can the read that named it be charged to it —
            // charging earlier silently dropped the most expensive call in a task's life.
            tickets.charge(taskId, read.usage());
            return result;
        }
        if (!bareKey) {
            return "error: could not read " + ref + " — pass an issue key (not a URL), or name the project";
        }
        String result = provisioning.initializeTask(newTask(ref, resolveProjects(project),
                readAndImplement(ref, request), request).build());
        // The read FAILED but may still have been paid for, and the key alone was enough to create the task —
        // so the one case where money bought nothing must not be the one case the task reports as free.
        tickets.charge(ref, read.usage());
        return result;
    }

    public String resume(String reviewRequestUrl) {
        return resumes.resume(reviewRequestUrl);
    }

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

    public static List<String> projectsMatching(TicketFacts facts, Map<String, List<String>> projectLabels) {
        Set<String> tokens = new HashSet<>(facts.labels());
        tokens.add(facts.trackerProject());
        return projectLabels.entrySet().stream()
                .filter(entry -> entry.getValue() != null
                        && entry.getValue().stream().anyMatch(tokens::contains))
                .map(Map.Entry::getKey)
                .toList();
    }

    public String resolveProject(String project) {
        return resolveProjects(project).get(0);
    }

    /**
     * Comma-separated and in the order given — the FIRST is where the agent's session runs — or the only project
     * configured.
     */
    public List<String> resolveProjects(String project) {
        Set<String> keys = configService.load().projects().keySet();
        if (project != null && !project.isBlank()) {
            List<String> named = Arrays.stream(project.split(",")).map(String::strip)
                    .filter(key -> !key.isEmpty()).distinct().toList();
            List<String> unknown = named.stream().filter(key -> !keys.contains(key)).toList();
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("unknown project " + unknown + ". Configured: " + keys);
            }
            if (named.isEmpty()) {
                throw new IllegalArgumentException("no project named. Configured: " + keys);
            }
            return named;
        }
        if (keys.size() == 1) {
            return List.of(keys.iterator().next());
        }
        throw new IllegalArgumentException("multiple projects " + keys + " — specify one");
    }

    private static NewTask.Builder newTask(String taskId, List<String> projectKeys, String instructions,
                                           LaunchRequest request) {
        return NewTask.builder(taskId, projectKeys.get(0))
                .alsoIn(projectKeys.subList(1, projectKeys.size()))
                .instructions(instructions)
                .mode(request.mode())
                .branchStrategy(request.strategy())
                .baseBranch(request.baseBranch());
    }

    private static String readAndImplement(String ref, LaunchRequest request) {
        return withNotes("Read " + ref + " via your issue-tracker MCP and implement it.", request.notes());
    }

    private static String withNotes(String instructions, String notes) {
        return notes == null || notes.isBlank()
                ? instructions
                : instructions + "\n\nAdditional instructions from the human:\n" + notes;
    }
}
