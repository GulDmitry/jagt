package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.task.StopTriage;
import dev.jagt.orchestrator.task.TaskStop;

public record StopTriageCase(
    String name,
    TaskStop.Trigger trigger,
    String evidence,
    String trail,
    String commits,
    StopTriage.Who who,
    StopTriage.Lift lift,
    String family) {}
