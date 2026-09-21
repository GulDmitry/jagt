package dev.jagt.orchestrator.protocol;

/**
 * One thing wrong with a message that crossed into jagt, named by the field whoever sent it must fix. The sender
 * is usually a model, so the text is the correction rather than a complaint.
 */
public record Violation(String field, String expected) {

    @Override
    public String toString() {
        return field + ": " + expected;
    }
}
