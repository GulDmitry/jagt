package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.TaskName;
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
 * The one place a task is started. It owns the decisions launching needs and nothing about how the request arrived.
 */
@Service
public class TaskLauncher {

    /** A bare issue key like {@code ABC-123}, as opposed to a url — never parsed OUT of one. */
    private static final Pattern KEY_REF = Pattern.compile("[A-Za-z][A-Za-z0-9]*-[0-9]+");

    /** A ticket titles a card in a line; a task somebody typed gets the same room and no more. */
    private static final int TITLE_MAX = 80;

    private final TaskProvisioning provisioning;
    private final TicketReader tickets;
    private final ConfigService configService;
    private final TaskResume resumes;

    public TaskLauncher(TaskProvisioning provisioning, TicketReader tickets, ConfigService configService,
                        TaskResume resumes) {
        this.provisioning = provisioning;
        this.tickets = tickets;
        this.configService = configService;
        this.resumes = resumes;
    }

    /**
     * Spins up a task for {@code ref}, an issue key or a URL to it in any tracker, or for the words a line
     * opening on a project key carries instead. Throws {@link IllegalArgumentException} when the request itself
     * is unusable. NO TASK NAMING AN ITEM IS CREATED WITHOUT THAT ITEM'S OWN FACTS: a later read cannot tell an
     * item that has no link from one that was never reached.
     */
    public Launched launchLine(String line) {
        return launch(LaunchRequest.ofLine(line, configService.load().projects().keySet()));
    }

    public Launched launch(LaunchRequest request) {
        if (request.ref() == null) {
            return launchWritten(request);
        }
        String ref = request.ref();
        String project = request.project();
        String strategy = request.strategy();
        boolean bareKey = KEY_REF.matcher(ref).matches();

        // Warn before spending a ticket read on a task that would only collide later.
        if (bareKey && BranchStrategy.of(strategy) == BranchStrategy.FRESH) {
            String existing = provisioning.existingBranchProject(ref,
                    project == null ? List.of() : resolveProjects(project));
            if (existing != null) {
                return Launched.refused("branch '" + ref + "' already exists in " + existing + " (previous run"
                        + " of this ticket). Say which: " + choice(BranchStrategy.RECREATE) + ", or "
                        + choice(BranchStrategy.RESUME) + ".");
            }
        }
        // An unknown project is settled before the read, not after paying for one.
        List<String> chosen = project != null ? resolveProjects(project) : null;

        // The read answers with the canonical key, which is what names the branch and the worktree.
        var read = tickets.read(ref);
        // Three different answers: one names a missing item, the others a read that never got there.
        if (read.facts().isEmpty()) {
            return Launched.refused("error: read failed: " + ref + " (cause in the log) — no task created");
        }
        if (!read.facts().get().exists()) {
            return Launched.refused("error: no such item: " + ref + " (the tracker says so) — no task"
                    + " created");
        }
        var facts = read.facts().filter(TicketFacts::usable);
        if (facts.isEmpty()) {
            return Launched.refused("error: read incomplete: " + ref + " (no key, title or url) — no task"
                    + " created");
        }
        TicketFacts f = facts.get();
        if (bareKey && !ref.equalsIgnoreCase(f.key())) {
            return Launched.refused("error: asked for " + ref + " and got " + f.key() + " back — no task"
                    + " created. Launch it under the key the tracker itself reports.");
        }
        String taskId = f.key();
        List<String> resolved = chosen != null ? chosen : List.of(resolveByLabels(f));
        String instructions = withNotes("Implement " + taskId + " — \"" + f.title()
                + "\". Read it via your issue-tracker MCP for full details, then work.", request.notes());
        String result = provisioning.initializeTask(newTask(taskId, resolved, instructions, request)
                .title(f.title()).ticketUrl(f.url()).build());
        // Only NOW does the task exist, so only now can the read that named it be charged to it.
        tickets.charge(taskId, read.usage());
        return Launched.created(taskId, result);
    }

    /**
     * A task nobody filed: the human's own words are its instructions, and they name its branch too, since a task
     * IS its branch and no tracker handed this one a key.
     */
    private Launched launchWritten(LaunchRequest request) {
        String written = request.notes();
        if (written == null || written.isBlank()) {
            return Launched.refused("error: no ticket and nothing to do — say what the task is: "
                    + LaunchRequest.OWN_GRAMMAR);
        }
        List<String> projects = resolveProjects(request.project());
        String named = TaskName.from(written);
        if (named == null) {
            return Launched.refused("error: nothing in that line can name a branch — open it with a word: "
                    + LaunchRequest.OWN_GRAMMAR);
        }
        String taskId = provisioning.freeTaskName(named, projects);
        return Launched.created(taskId, provisioning.initializeTask(
                newTask(taskId, projects, written, request).title(titleOf(written)).build()));
    }

    /** The card's own words for a task no tracker titled. */
    private static String titleOf(String written) {
        String head = written.strip().lines().findFirst().orElse("").strip();
        return head.length() <= TITLE_MAX ? head : head.substring(0, TITLE_MAX).strip() + "…";
    }

    public Launched resume(String reviewRequestUrl) {
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

    /** In the order given, the FIRST being where the agent's session runs; or the only project configured. */
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

    private static String withNotes(String instructions, String notes) {
        return notes == null || notes.isBlank()
                ? instructions
                : instructions + "\n\nAdditional instructions from the human:\n" + notes;
    }

    private static String choice(BranchStrategy strategy) {
        return strategy.id() + " (" + strategy.hint() + ")";
    }
}
