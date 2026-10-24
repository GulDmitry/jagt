package dev.jagt.orchestrator.adapter.replay;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The headless reads' answers per call kind, in recorded order. How often a read polls is the clock's, not a model's,
 * so the last answer stands for every later call.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "replay")
public class RecordedReads {

    private final Recording recording;
    private final Map<String, Deque<JsonNode>> answers = new HashMap<>();

    public RecordedReads(Recording recording) {
        this.recording = recording;
        recording.lines().stream().filter(RecordingLine.Read.class::isInstance).map(RecordingLine.Read.class::cast)
                .forEach(read -> answers.computeIfAbsent(read.kind(), kind -> new ArrayDeque<>()).add(read.answer()));
    }

    /** Empty where the run never made this read: the replay diverges there. */
    public synchronized Optional<JsonNode> answer(String kind, String ref) {
        Deque<JsonNode> left = answers.get(kind);
        if (left == null) {
            recording.diverge("", kind, "read " + ref + " was never made in the recording");
            return Optional.empty();
        }
        return Optional.of(left.size() > 1 ? left.removeFirst() : left.getFirst());
    }
}
