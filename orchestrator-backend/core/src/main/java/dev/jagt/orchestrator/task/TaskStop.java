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
        STALLED("rounds bring nothing new", "Give the session what the rounds lacked; if nothing was missing, report a jagt defect", true),
        SHIPS("ships with no new note", "Check a new note or commit waits to ship; if one does, report a jagt defect", true),
        MISS("no case for this step", "Set off by jagt or the Master: report a jagt defect; by a session: add a case if one should send it"),
        ROUNDS("same round sent too often", "Answer the session's question, or give it what it lacked"),
        PACE("too many unattended moves in an hour", "Read the last hour's moves: say what each one changed"),
        CHECKS("checks cannot be read", "Check that the code host's MCP can list the pipelines", true),
        UNDELIVERED("a step never landed", "Check that the session's window is running"),
        UNCHANGED_FAILURE("same failure again", "Change the tree, or fix the check itself: its access or a file it needs", true);


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
