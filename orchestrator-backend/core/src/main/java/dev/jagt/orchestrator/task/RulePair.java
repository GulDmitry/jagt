package dev.jagt.orchestrator.task;

/** Two written rules saying one thing: {@code duplicate} is the wording that goes, {@code none} where no two do. */
public record RulePair(String kept, String duplicate) {

    public RulePair {
        kept = kept == null ? "" : kept.strip();
        duplicate = duplicate == null ? "" : duplicate.strip();
    }
}
