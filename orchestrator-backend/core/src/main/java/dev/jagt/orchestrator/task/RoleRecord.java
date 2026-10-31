package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What a checker left on a task: the step whose read is running, the tips of the deploy it passed, and the digest and rows
 * of the deploy a round went out on, kept for the decline read. An empty string is none.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RoleRecord(String reading, String passed, String roundDigest, String roundRows) {

    public RoleRecord {
        reading = reading == null ? "" : reading;
        passed = passed == null ? "" : passed;
        roundDigest = roundDigest == null ? "" : roundDigest;
        roundRows = roundRows == null ? "" : roundRows;
    }

    public static RoleRecord defaults() {
        return new RoleRecord("", "", "", "");
    }

    public RoleRecord withReading(String reading) {
        return new RoleRecord(reading, passed, roundDigest, roundRows);
    }

    public RoleRecord withPassed(String passed) {
        return new RoleRecord(reading, passed, roundDigest, roundRows);
    }

    public RoleRecord withRound(String digest, String rows) {
        return new RoleRecord(reading, passed, digest, rows);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return equals(defaults());
    }

    public boolean passedOn(String tips) {
        return !passed.isEmpty() && passed.equals(tips);
    }
}
