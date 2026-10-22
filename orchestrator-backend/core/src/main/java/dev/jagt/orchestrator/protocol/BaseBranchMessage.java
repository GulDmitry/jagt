package dev.jagt.orchestrator.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static dev.jagt.orchestrator.protocol.Wire.absent;

/** A task's base moved after it was cut, as its session reports it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BaseBranchMessage(@Nullable String branch, @Nullable String taskId) implements Message {

    public static final Schema SCHEMA = Schema
            .of("Tell jagt this task's base branch moved, after you rebased onto it on the human's or the Master's"
                    + " word. Ship then targets the review request at it and the diff is read against it. jagt"
                    + " checks that origin has the branch and your HEAD holds it.")
            .required("branch", "string", "The new base as origin names it, e.g. ABC-41.")
            .text("taskId", "Optional explicit task id or alias (Master use). Sub-agents may only target their"
                    + " own task.");

    public BaseBranchMessage {
        branch = absent(branch);
        taskId = absent(taskId);
    }

    @Override
    public List<Violation> violations(MessageContext context) {
        return branch == null ? List.of(new Violation("branch", "required: the base you rebased onto")) : List.of();
    }
}
