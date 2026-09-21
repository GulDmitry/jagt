package dev.jagt.orchestrator.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Starting a fresh session for a task that already exists. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionStart(String taskId, String mode) implements Message {

    public static final List<String> MODES = List.of("auto", "plan");

    public static final Schema SCHEMA = Schema
            .of("Start a fresh sub-agent session (a terminal window) for an ALREADY registered task whose"
                    + " session is gone or unresponsive.")
            .required("taskId", "string", "Task id or its short alias.")
            .choice("mode", MODES, "plan = start the agent in its planning mode, which writes plan.md and stops."
                    + " Default: auto.");

    public SessionStart {
        taskId = taskId == null || taskId.isBlank() ? null : taskId;
        mode = mode == null || mode.isBlank() ? null : mode;
    }

    @Override
    public List<Violation> violations(MessageContext context) {
        List<Violation> found = new ArrayList<>();
        if (taskId == null) {
            found.add(new Violation("taskId", "required: the task to start a session for"));
        }
        if (mode != null && !MODES.contains(mode.strip().toLowerCase(Locale.ROOT))) {
            found.add(new Violation("mode", "one of " + MODES + ", or left out"));
        }
        return List.copyOf(found);
    }
}
