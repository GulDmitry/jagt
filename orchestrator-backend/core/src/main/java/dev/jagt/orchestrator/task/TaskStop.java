package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Why jagt stopped a task for the human, and when; a human's lift clears it, or a new request replacing the one
 * it was counted on. {@code request} holds that request's URLs, blank-separated; null where none was counted on.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskStop(Trigger trigger, long at, String evidence, @Nullable String request) {

    public TaskStop(Trigger trigger, long at, String evidence) {
        this(trigger, at, evidence, null);
    }

    /** {@code next} is what a human reads after the evidence. */
    public enum Trigger {
        STALLED(Trigger.DEFECT, true), SHIPS(Trigger.DEFECT, true), MISS("Add a case if one should send it"),
        ROUNDS("Read what the session made of the last one"),
        CHECKS("Check that the code host's MCP can list the pipelines"),
        UNDELIVERED("Check that the session's window is running"),
        UNCHANGED_FAILURE("Change the tree, or read why the failure stands");

        private static final String DEFECT = "Likely a jagt defect: inspect the sweeps in the log";

        private final String next;
        private final boolean countedOnRequest;

        Trigger(String next) {
            this(next, false);
        }

        Trigger(String next, boolean countedOnRequest) {
            this.next = next;
            this.countedOnRequest = countedOnRequest;
        }
    }

    /** The stop counted on {@code requests}, when its trigger counts on a request at all. */
    public TaskStop countedOn(List<String> requests) {
        return trigger.countedOnRequest && !requests.isEmpty()
                ? new TaskStop(trigger, at, evidence, String.join(" ", requests)) : this;
    }

    /** True where the request this stop was counted on is no longer among {@code requests}. */
    public boolean voidedBy(List<String> requests) {
        return request != null && !requests.containsAll(Arrays.asList(request.split(" ")));
    }

    public String reading() {
        return evidence + ". " + trigger.next + ", then lift or close";
    }
}
