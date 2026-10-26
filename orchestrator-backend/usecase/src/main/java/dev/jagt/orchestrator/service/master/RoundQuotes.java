package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** What jagt reads for a Master read once; empty where what the human typed could not be read. */
@Service
@RequiredArgsConstructor
public class RoundQuotes {

    private final RoundFacts facts;
    private final MasterDecisions decisions;
    private final MasterReview reviews;

    Optional<MasterPanel.RoundRead> round(String taskId, TaskState task) {
        return facts.humanSaid(task).map(said -> new MasterPanel.RoundRead(facts.ask(taskId, task), facts.diff(task),
                decisions.of(task), said, facts.notes(task)));
    }

    Optional<MasterPanel.RoundRead> plan(String taskId, TaskState task) {
        return facts.humanSaid(task).map(said -> new MasterPanel.RoundRead(facts.ask(taskId, task), "",
                decisions.of(task), said, facts.notes(task)));
    }

    Optional<MasterPanel.RoundRead> question(TaskState task) {
        return facts.humanSaid(task).map(said -> new MasterPanel.RoundRead("", "", decisions.of(task), said, ""));
    }

    Optional<List<String>> paths(TaskState task) {
        return facts.paths(task);
    }

    Optional<String> ownDiff(TaskState task) {
        return facts.ownDiff(task);
    }

    /**
     * Digest of the stored texts a round quotes: the ticket, what the human said, the session's notes and the probe
     * results. Empty where what the human typed could not be read. The decisions are left out: acting on a verdict
     * records them, so they differ at every hand-back.
     */
    Optional<String> inputs(String taskId, TaskState task) {
        return facts.humanSaid(task).map(said -> Turn.digestOf(String.join("\u0000", facts.ask(taskId, task),
                String.join("\n", said), facts.notes(task),
                task.probes().stream().map(Probe::result).collect(Collectors.joining("\n")))));
    }

    /** A verdict, ready or not, still stands while the hand-back's inputs are the ones it read; else it is read again. */
    boolean carried(TaskState task, String inputs) {
        Optional<String> own = facts.ownDiff(task);
        boolean same = own.isPresent() && reviews.stored(task)
                .filter(review -> review.readsInputsOf(task, own.get(), inputs))
                .filter(review -> MasterReview.Verdict.of(review).kind() != MasterReview.Kind.QUESTION).isPresent();
        return same && reviews.restamp(task);
    }

    String planFile(TaskState task) {
        return facts.plan(task);
    }
}
