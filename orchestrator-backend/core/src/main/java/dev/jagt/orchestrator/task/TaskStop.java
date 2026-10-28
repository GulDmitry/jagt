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

    /** {@code reason} is the stop in five words or fewer; {@code next} is what a human reads after the evidence. */
    public enum Trigger {
        STALLED("rounds bring nothing new", Trigger.DEFECT, true),
        SHIPS("ships with no new note", Trigger.DEFECT, true),
        MISS("no case for this step", "Add a case if one should send it"),
        ROUNDS("same round sent too often", "Read what the session made of the last one"),
        CHECKS("checks cannot be read", "Check that the code host's MCP can list the pipelines", true),
        UNDELIVERED("a step never landed", "Check that the session's window is running"),
        UNCHANGED_FAILURE("same failure again", "Change the tree, or read why the failure stands", true);

        private static final String DEFECT = "Likely a jagt defect: inspect the sweeps in the log";

        private final String reason;
        private final String next;
        private final boolean countedOnRequest;

        Trigger(String reason, String next) {
            this(reason, next, false);
        }

        Trigger(String reason, String next, boolean countedOnRequest) {
            this.reason = reason;
            this.next = next;
            this.countedOnRequest = countedOnRequest;
        }

        public String reason() {
            return reason;
        }

        public String next() {
            return next;
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
