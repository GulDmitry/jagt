package dev.jagt.orchestrator.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Locale;

/** Opening a task's worktree for a human to look at, as a project or as a snapshot diff. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IdeOpen(String taskId, String mode) implements Message {

    public static final List<String> MODES = List.of("diff", "project");

    public static final Schema SCHEMA = Schema
            .of("Open a task in the configured editor. mode 'project' (default) opens the worktree as a full"
                    + " project (needed to run the app; use Git → Local Changes for a live diff vs base);"
                    + " mode 'diff' opens a STATIC snapshot diff vs base — it does NOT auto-refresh (re-run to"
                    + " update). taskId defaults to the calling worktree's task.")
            .text("taskId", "Task id or its short alias; the calling worktree's own task where it is left out.")
            .choice("mode", MODES, "Default: project.");

    public IdeOpen {
        taskId = taskId == null || taskId.isBlank() ? null : taskId;
        mode = mode == null || mode.isBlank() ? null : mode;
    }

    @Override
    public List<Violation> violations(MessageContext context) {
        return mode != null && !MODES.contains(mode.strip().toLowerCase(Locale.ROOT))
                ? List.of(new Violation("mode", "one of " + MODES + ", or left out"))
                : List.of();
    }
}
