package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ReviewRequestTitle;
import dev.jagt.orchestrator.task.TaskName;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Registers a task resumed on an open request, titled and linked from its ticket where its branch names one. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeRegistration {

    private final TaskProvisioning provisioning;
    private final AgentStatusReports statusReports;
    private final TicketReader tickets;

    public Launched register(String taskId, String project, String mrUrl, String title, String targetBranch) {
        // Stored bare: the pattern already prefixed the ticket, and a later ship expands it again.
        String bare = ReviewRequestTitle.stripTicketPrefix(title, taskId);
        boolean untitled = bare == null || bare.isBlank();
        // No request carries the ticket's LINK, so a ticket-keyed branch is read like `do` reads one.
        boolean asksTheTracker = TaskName.isTicketKey(taskId);
        Answer<TicketFacts> ticket = asksTheTracker ? tickets.read(taskId) : Answer.unavailable();
        // An answer about another item titles another card: the branch already fixed which one this is.
        TicketFacts named = ticket.facts().filter(TicketFacts::usable)
                .filter(item -> taskId.equalsIgnoreCase(item.key())).orElse(null);
        // Refused as `do` refuses it: a card with no ticket link cannot be told from one never read.
        if (asksTheTracker && named == null) {
            String other = ticket.facts().filter(TicketFacts::usable).map(TicketFacts::key).orElse(null);
            log.atWarn().setMessage("resume refused")
                    .addKeyValue("task", taskId)
                    .addKeyValue("cause", other == null ? "ticket unread" : "tracker answered " + other)
                    .addKeyValue("effect", "no task created")
                    .log();
            return Launched.refused(other == null
                    ? "error: ticket read failed: " + taskId + " (cause in the log) — no task created"
                    : "error: asked for " + taskId + " and got " + other + " back — no task created");
        }
        String instructions = "Reopened for review. Your branch is resumed with its existing commits and"
                + " review request " + mrUrl + " is open — there is NOTHING to build or commit right now."
                + " FIRST run `git status`: a rebase onto " + targetBranch + " in progress is jagt's, and it"
                + " conflicted — resolve the conflicts, `git rebase --continue`, then `git push"
                + " --force-with-lease origin " + taskId + "` (this brief is the permission, for THIS branch"
                + " only), and report `question` where a resolution was not obvious. Otherwise do NOT"
                + " re-implement and"
                + " do NOT call update_agent_status: the Master has already set your status (CI_POLLING). Stay"
                + " idle; only when the Master relays review comments via task_context.md do you address them.";
        provisioning.initializeTask(NewTask.builder(taskId, project)
                .instructions(instructions).branchStrategy("resume")
                // The request's own words where it has any: they are what the reviewer was shown.
                .title(untitled && named != null ? named.title() : bare)
                .ticketUrl(named == null ? null : named.url())
                // The open request's OWN target, the host matching source AND target.
                .baseBranch(targetBranch)
                .build());
        // The task exists only now, so only now can the read that titled it be charged to it.
        if (asksTheTracker) {
            tickets.charge(taskId, ticket.usage());
        }
        statusReports.report(FlowRules.resumedOnARequest(), "review request: " + mrUrl, taskId);
        return Launched.created(taskId, "Resumed " + taskId + " on its existing branch, linked " + mrUrl
                + "; CI_POLLING — `sweep` or `deploy`.");
    }
}
