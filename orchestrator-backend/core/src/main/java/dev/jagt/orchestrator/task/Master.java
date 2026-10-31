package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What the Master left on a task: the step it offered the human instead of pressing, the digest of the question it
 * gave no usable answer to, the arrival at which it pressed deploy, and the fingerprint and round of the verdict
 * jagt wrote for it. An empty string and a zero round are none.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Master(String left, String unanswered, String pressedDeploy, String seal, long sealedRound) {

    public Master {
        left = left == null ? "" : left;
        unanswered = unanswered == null ? "" : unanswered;
        pressedDeploy = pressedDeploy == null ? "" : pressedDeploy;
        seal = seal == null ? "" : seal;
    }

    public static Master defaults() {
        return new Master("", "", "", "", 0);
    }

    public Master withLeft(String left) {
        return new Master(left, unanswered, pressedDeploy, seal, sealedRound);
    }

    public Master withUnanswered(String unanswered) {
        return new Master(left, unanswered, pressedDeploy, seal, sealedRound);
    }

    public Master withPressedDeploy(String arrival) {
        return new Master(left, unanswered, arrival, seal, sealedRound);
    }

    public Master withSeal(String seal, long round) {
        return new Master(left, unanswered, pressedDeploy, seal, round);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return equals(defaults());
    }
}
