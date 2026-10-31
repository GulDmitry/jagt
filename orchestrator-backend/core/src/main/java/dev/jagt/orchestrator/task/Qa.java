package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What QA left on a task: the step whose read is running, the tips of the deploy it passed, and the digest and rows
 * of the deploy a round went out on, kept for the decline read. An empty string is none.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Qa(String reading, String passed, String roundDigest, String roundRows) {

    public Qa {
        reading = reading == null ? "" : reading;
        passed = passed == null ? "" : passed;
        roundDigest = roundDigest == null ? "" : roundDigest;
        roundRows = roundRows == null ? "" : roundRows;
    }

    public static Qa defaults() {
        return new Qa("", "", "", "");
    }

    public Qa withReading(String reading) {
        return new Qa(reading, passed, roundDigest, roundRows);
    }

    public Qa withPassed(String passed) {
        return new Qa(reading, passed, roundDigest, roundRows);
    }

    public Qa withRound(String digest, String rows) {
        return new Qa(reading, passed, digest, rows);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return equals(defaults());
    }

    public boolean passedOn(String tips) {
        return !passed.isEmpty() && passed.equals(tips);
    }
}
