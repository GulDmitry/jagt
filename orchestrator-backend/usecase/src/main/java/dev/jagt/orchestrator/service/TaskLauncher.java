package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
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
import java.util.Optional;
import java.util.Set;

/**
 * The one place a task is started. It owns the decisions launching needs and nothing about how the request arrived.
 */
@Service
public class TaskLauncher {

    /** A ticket titles a card in a line; a task somebody typed gets the same room and no more. */
    private static final int TITLE_MAX = 80;

    private final TaskProvisioning provisioning;
    private final TicketReader tickets;
    private final ConfigService configService;
    private final TaskResume resumes;
    private final ProjectRouting routing;

    public TaskLauncher(TaskProvisioning provisioning, TicketReader tickets, ConfigService configService,
                        TaskResume resumes, ProjectRouting routing) {
        this.provisioning = provisioning;
        this.tickets = tickets;
        this.configService = configService;
        this.resumes = resumes;
        this.routing = routing;
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
        Optional<Launched> refused = refusedForExistingBranch(request);
        if (refused.isPresent()) {
            return refused.get();
        }
        // An unknown project is settled before the read, not after paying for one.
        List<String> chosen = request.project() != null ? resolveProjects(request.project()) : null;
        // The read answers with the canonical key, which is what names the branch and the worktree.
        return launched(request, tickets.read(request.ref()), chosen);
    }

    /**
     * The same launch with the item's facts already read and PAID FOR. Intake reads them to decide the item may
     * be started at all, and paying a second time for the one answer is a cost jagt need not carry.
     */
    public Launched launch(LaunchRequest request, Answer<TicketFacts> read) {
        LaunchRequest settled = request.strategy() != null || request.project() == null ? request
                : request.withStrategy(provisioning.strategyForExisting(request.ref(), request.project()).id());
        return refusedForExistingBranch(settled).orElseGet(() -> launched(settled, read,
                settled.project() != null ? resolveProjects(settled.project()) : null));
    }

    /** Warns before a task is started that would only collide later; empty where nothing is in the way. */
    private Optional<Launched> refusedForExistingBranch(LaunchRequest request) {
        String ref = request.ref();
        if (!TaskName.isTicketKey(ref) || BranchStrategy.of(request.strategy()) != BranchStrategy.FRESH) {
            return Optional.empty();
        }
        String existing = provisioning.existingBranchProject(ref,
                request.project() == null ? List.of() : resolveProjects(request.project()));
        return existing == null ? Optional.empty()
                : Optional.of(Launched.refused("branch '" + ref + "' already exists in " + existing
                        + " (previous run of this ticket). Say which: " + choice(BranchStrategy.RECREATE)
                        + ", or " + choice(BranchStrategy.RESUME) + "."));
    }

    private Launched launched(LaunchRequest request, Answer<TicketFacts> read, List<String> chosen) {
        String ref = request.ref();
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
        if (TaskName.isTicketKey(ref) && !ref.equalsIgnoreCase(f.key())) {
            return Launched.refused("error: asked for " + ref + " and got " + f.key() + " back — no task"
                    + " created. Launch it under the key the tracker itself reports.");
        }
        String taskId = f.key();
        // A human who named a project has settled it; only an unplaced one is worth asking about.
        ProjectRouting.Placement placement = chosen != null ? null : routing.projectFor(f);
        List<String> resolved = chosen != null ? chosen : placement.project().map(List::of).orElse(null);
        if (resolved == null) {
            return Launched.refused("error: " + taskId + " not placed in a configured project: "
                    + placement.reason() + " — say which: do " + taskId + " <project>");
        }
        String instructions = withNotes("Implement " + taskId + " — \"" + f.title()
                + "\". Read it via your issue-tracker MCP for full details, then work.", request.notes());
        String result = provisioning.initializeTask(newTask(taskId, resolved, instructions, request)
                .title(f.title()).ticketUrl(f.url()).build());
        // Only where the human named it: their word against a rule is the correction, and the router's own
        // placement contradicts nothing.
        if (chosen != null) {
            routing.placedByHand(taskId, resolved.get(0));
        }
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
