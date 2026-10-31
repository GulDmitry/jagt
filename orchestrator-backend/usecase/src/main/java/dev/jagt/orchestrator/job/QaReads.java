package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.QaReader;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A QA read held as a turn: open while the run reads, closed before what it found is acted on. */
@Component
@RequiredArgsConstructor
public class QaReads {

    private final QaReader reader;
    private final TaskTurns turns;

    public Answer<QaVerdict> check(String taskId, String step, String digest, QaReader.Run run) {
        turns.opened(taskId, step, digest, TaskStatus.DEPLOYED);
        try {
            return reader.read(run);
        } finally {
            turns.closed(taskId, Checkers.QA, step, digest);
        }
    }

    public Answer<QaDecline> decline(String taskId, String step, String digest, QaReader.Run run) {
        turns.opened(taskId, step, digest, TaskStatus.DEPLOYED);
        try {
            return reader.reconsider(run);
        } finally {
            turns.closed(taskId, Checkers.QA, step, digest);
        }
    }
}
