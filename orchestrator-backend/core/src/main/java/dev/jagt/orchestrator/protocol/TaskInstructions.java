package dev.jagt.orchestrator.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/** The one instruction standing for a task right now, written into its worktree. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskInstructions(String taskId, String instructions) implements Message {

    public static final Schema SCHEMA = Schema
            .of("Write instructions to <worktree>/task_context.md of a task. Used by the Master's automated"
                    + " ship/review steps; not for ad-hoc human notes (the human talks to the agent directly in"
                    + " its tmux window).")
            .required("taskId", "string", "Task id or its short alias.")
            .required("instructions", "string", "What the session is to do now. It REPLACES whatever stood"
                    + " there, so it says everything that still applies.");

    public TaskInstructions {
        taskId = taskId == null || taskId.isBlank() ? null : taskId;
        instructions = instructions == null || instructions.isBlank() ? null : instructions;
    }

    @Override
    public List<Violation> violations(MessageContext context) {
        List<Violation> found = new ArrayList<>();
        if (taskId == null) {
            found.add(new Violation("taskId", "required: the task whose instruction this is"));
        }
        if (instructions == null) {
            found.add(new Violation("instructions", "required: a relay with nothing in it erases the one"
                    + " standing instruction"));
        }
        return List.copyOf(found);
    }
}
