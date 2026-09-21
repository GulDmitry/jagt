package dev.jagt.orchestrator.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jagt.orchestrator.task.BranchStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Everything a task needs to exist before any of it has been cut. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NewTaskMessage(String taskId, String projectKey, List<String> alsoProjects, String instructions,
                             String title, String ticketUrl, String mode, String branchStrategy,
                             String baseBranch) implements Message {

    public static final Schema SCHEMA = Schema
            .of("Create an isolated Git worktree for a task, register it in state.json and start a sub-agent in"
                    + " its own session. Master-only.")
            .required("taskId", "string", "Task id, e.g. ABC-123 — any name git accepts as a branch. It BECOMES"
                    + " the branch name.")
            .required("projectKey", "string", "Project key from jagt.yml — where the agent's session runs.")
            .texts("alsoProjects", "Further project keys the SAME session works in: one worktree each, one"
                    + " branch name, one review round per repository. For a change that spans repositories (a"
                    + " service and its client); omit for ordinary work.")
            .text("instructions", "Optional initial instructions, written to task_context.md in the new"
                    + " worktree.")
            .text("title", "The ticket title, shown on the card while the task is in development.")
            .text("ticketUrl", "Canonical web link to the ticket, which the card's task number opens.")
            .choice("mode", SessionStart.MODES, "plan = the agent starts in its planning mode. Default: auto.")
            .choice("branchStrategy", BranchStrategy.ids(), "What to do when a branch of this name already"
                    + " exists (a reopened ticket). Default: " + BranchStrategy.FRESH.id() + ".")
            .text("baseBranch", "Branch to cut the worktree from and to target with the review request, e.g. a"
                    + " parent feature branch. Must exist on origin. Default: the project's configured"
                    + " baseBranch.");

    public NewTaskMessage {
        taskId = absent(taskId);
        projectKey = absent(projectKey);
        instructions = absent(instructions);
        title = absent(title);
        ticketUrl = absent(ticketUrl);
        mode = absent(mode);
        branchStrategy = absent(branchStrategy);
        baseBranch = absent(baseBranch);
        alsoProjects = alsoProjects == null ? List.of() : List.copyOf(alsoProjects);
    }

    @Override
    public List<Violation> violations(MessageContext context) {
        List<Violation> found = new ArrayList<>();
        if (taskId == null) {
            found.add(new Violation("taskId", "required: it becomes the branch name"));
        }
        if (projectKey == null) {
            found.add(new Violation("projectKey", "required: a key from jagt.yml, where the session runs"));
        }
        if (mode != null && !SessionStart.MODES.contains(lower(mode))) {
            found.add(new Violation("mode", "one of " + SessionStart.MODES + ", or left out"));
        }
        if (branchStrategy != null && !BranchStrategy.ids().contains(lower(branchStrategy))) {
            found.add(new Violation("branchStrategy", "one of " + BranchStrategy.ids() + ", or left out"));
        }
        if (ticketUrl != null && !ticketUrl.startsWith("http://") && !ticketUrl.startsWith("https://")) {
            found.add(new Violation("ticketUrl", "an http(s) link the human can open, not " + ticketUrl));
        }
        if (projectKey != null && alsoProjects.contains(projectKey)) {
            found.add(new Violation("alsoProjects", "the further repositories only; " + projectKey
                    + " is already where the session runs"));
        }
        return List.copyOf(found);
    }

    private static String lower(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String absent(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
