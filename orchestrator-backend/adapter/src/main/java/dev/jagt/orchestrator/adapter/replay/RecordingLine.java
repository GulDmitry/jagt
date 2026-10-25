package dev.jagt.orchestrator.adapter.replay;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** One line of {@code recording.jsonl}: what a model did in a run, keyed to the turn or round it answered. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RecordingLine.Opened.class, name = "turn"),
        @JsonSubTypes.Type(value = RecordingLine.Called.class, name = "call"),
        @JsonSubTypes.Type(value = RecordingLine.Wrote.class, name = "tree"),
        @JsonSubTypes.Type(value = RecordingLine.Pushed.class, name = "push"),
        @JsonSubTypes.Type(value = RecordingLine.Judged.class, name = "verdict"),
        @JsonSubTypes.Type(value = RecordingLine.Read.class, name = "read"),
        @JsonSubTypes.Type(value = RecordingLine.Answered.class, name = "answer")})
public sealed interface RecordingLine {

    /** Turn 0 is the session's launch, which no relay opens. */
    long LAUNCH = 0;

    /** What a session did inside the turn it answered. */
    sealed interface Move extends RecordingLine {

        String task();

        long turn();
    }

    record Opened(String task, long turn, String step) implements RecordingLine {
    }

    record Called(String task, long turn, String tool, JsonNode arguments) implements Move {
    }

    /** Files the session's tree held, by path relative to the worktree. */
    record Wrote(String task, long turn, Map<String, String> files) implements Move {
    }

    /** One commit of {@code paths} and a push of the task's branch. */
    record Pushed(String task, long turn, String message, List<String> paths) implements Move {
    }

    record Judged(long round, String role, Judgement judgement) implements RecordingLine {
    }

    /** A headless read's answer as the model gave it, by its call kind. */
    record Read(String kind, String ref, JsonNode answer) implements RecordingLine {
    }

    /** The decision the Master gave a session's question, flattened to one line. */
    record Answered(String question, String decision) implements RecordingLine {
    }
}
