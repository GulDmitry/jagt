package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** What jagt reads for a Master read once; empty where what the human typed could not be read. */
@Service
@RequiredArgsConstructor
public class RoundQuotes {

    private final RoundFacts facts;
    private final MasterDecisions decisions;

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

    String planFile(TaskState task) {
        return facts.plan(task);
    }
}
