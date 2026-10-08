package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.TaskName;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * A launch whose projects are already resolved: a ticket's, created only from that item's own facts, or a written
 * line's. {@code chosen} is null where no human named a project.
 */
@Service
@RequiredArgsConstructor
public class TaskLaunches {

    /** A ticket titles a card in a line; a task somebody typed gets the same room and no more. */
    private static final int TITLE_MAX = 80;

    private final TaskProvisioning provisioning;
    private final NewTaskWorktrees worktrees;
    private final TicketReader tickets;
    private final ProjectRouting routing;

    public Launched ticket(LaunchRequest request, List<String> chosen) {
        // The read answers with the canonical key, which is what names the branch and the worktree.
        return refusedForExistingBranch(request, chosen)
                .orElseGet(() -> launched(request, tickets.read(request.ref()), chosen));
    }

    /**
     * The same launch with the item's facts already read and PAID FOR. Intake reads them to decide the item may
     * be started at all, and paying a second time for the one answer is a cost jagt need not carry.
     */
    public Launched ticket(LaunchRequest request, List<String> chosen, Answer<TicketFacts> read) {
        LaunchRequest settled = request.strategy() != null || request.project() == null ? request
                : request.withStrategy(worktrees.strategyForExisting(request.ref(), request.project()).id());
        return refusedForExistingBranch(settled, chosen).orElseGet(() -> launched(settled, read, chosen));
    }

    /**
     * A task nobody filed: the human's own words are its instructions, and they name its branch too, since a task
     * IS its branch and no tracker handed this one a key.
     */
    public Launched written(LaunchRequest request, List<String> projects) {
        String written = request.notes();
        String named = TaskName.from(written);
        if (named == null) {
            return Launched.refused("error: nothing in that line can name a branch — open it with a word: "
                    + LaunchRequest.OWN_GRAMMAR);
        }
        String taskId = worktrees.freeTaskName(named, projects);
        return Launched.created(taskId, provisioning.initializeTask(
                newTask(taskId, projects, written, request).title(titleOf(written)).build()));
    }

    /** Warns before a task is started that would only collide later; empty where nothing is in the way. */
    private Optional<Launched> refusedForExistingBranch(LaunchRequest request, List<String> chosen) {
        String ref = request.ref();
        if (!TaskName.isTicketKey(ref) || BranchStrategy.of(request.strategy()) != BranchStrategy.FRESH) {
            return Optional.empty();
        }
        String existing = worktrees.existingBranchProject(ref, chosen == null ? List.of() : chosen);
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

    /** The card's own words for a task no tracker titled. */
    private static String titleOf(String written) {
        String head = written.strip().lines().findFirst().orElse("").strip();
        return head.length() <= TITLE_MAX ? head : head.substring(0, TITLE_MAX).strip() + "…";
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
