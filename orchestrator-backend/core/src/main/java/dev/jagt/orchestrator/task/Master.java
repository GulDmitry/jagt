package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What the Master left on a task: the step it offered the human instead of pressing, the digest of the question it
 * gave no usable answer to, the arrival at which it pressed deploy, the fingerprint and round of the verdict
 * jagt wrote for it, the key of the stop it was last asked about, and the stops it lifted since the human's last
 * lift or deploy. An empty string and a zero round are none.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Master(String left, String unanswered, String pressedDeploy, String seal, long sealedRound,
                     String askedStop, Integer liftedStops) {

    public Master {
        left = left == null ? "" : left;
        unanswered = unanswered == null ? "" : unanswered;
        pressedDeploy = pressedDeploy == null ? "" : pressedDeploy;
        seal = seal == null ? "" : seal;
        askedStop = askedStop == null ? "" : askedStop;
        liftedStops = liftedStops == null ? 0 : liftedStops;
    }

    public static Master defaults() {
        return new Master("", "", "", "", 0, "", 0);
    }

    public Master withLeft(String left) {
        return new Master(left, unanswered, pressedDeploy, seal, sealedRound, askedStop, liftedStops);
    }

    public Master withUnanswered(String unanswered) {
        return new Master(left, unanswered, pressedDeploy, seal, sealedRound, askedStop, liftedStops);
    }

    public Master withPressedDeploy(String arrival) {
        return new Master(left, unanswered, arrival, seal, sealedRound, askedStop, liftedStops);
    }

    public Master withSeal(String seal, long round) {
        return new Master(left, unanswered, pressedDeploy, seal, round, askedStop, liftedStops);
    }

    public Master withAskedStop(String askedStop) {
        return new Master(left, unanswered, pressedDeploy, seal, sealedRound, askedStop, liftedStops);
    }

    public Master withLiftedStops(int liftedStops) {
        return new Master(left, unanswered, pressedDeploy, seal, sealedRound, askedStop, liftedStops);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return equals(defaults());
    }
}
