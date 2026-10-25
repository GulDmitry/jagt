package dev.jagt.orchestrator.adapter.replay;

import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Master's reads in recorded order. Roles read one round side by side, so within the earliest round left the
 * role a prompt names answers it, else the next read in order.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "replay")
public class RecordedVerdicts {

    private static final String ASKED = "The session working it stopped to ask:";

    private final Recording recording;
    private final List<RecordingLine.Judged> left;
    private final List<RecordingLine.Answered> answers;

    public RecordedVerdicts(Recording recording) {
        this.recording = recording;
        this.left = new ArrayList<>(recording.lines().stream().filter(RecordingLine.Judged.class::isInstance)
                .map(RecordingLine.Judged.class::cast).toList());
        this.answers = new ArrayList<>(recording.lines().stream().filter(RecordingLine.Answered.class::isInstance)
                .map(RecordingLine.Answered.class::cast).toList());
    }

    public synchronized Optional<Judgement> answer(String prompt) {
        if (prompt.contains(ASKED)) {
            return decision();
        }
        if (left.isEmpty()) {
            recording.diverge("", "master", "the Master read a round the recording holds no verdict for");
            return Optional.empty();
        }
        long round = left.getFirst().round();
        List<RecordingLine.Judged> current = left.stream().filter(read -> read.round() == round).toList();
        RecordingLine.Judged taken = current.stream().filter(read -> prompt.contains(" " + read.role() + " "))
                .findFirst().orElse(current.getFirst());
        left.remove(taken);
        return Optional.of(taken.judgement());
    }

    private Optional<Judgement> decision() {
        if (answers.isEmpty()) {
            recording.diverge("", "master", "the Master answered a question the recording holds no decision for");
            return Optional.empty();
        }
        Finding decided = new Finding("", answers.removeFirst().decision(), "", "", "", "");
        return Optional.of(new Judgement("", "ready", List.of(decided), "", List.of(), ""));
    }
}
