package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;

/** What can happen to a task's status, named by the door it came through. */
sealed interface TaskMove {

    record Ran(TaskAction action, Outcome.Kind outcome) implements TaskMove {
    }

    record Reported(TaskStatus status, boolean verificationOwed) implements TaskMove {
    }

    record Read(TaskStatus concluded) implements TaskMove {
    }

    record DeployRead(boolean redOnTheCode) implements TaskMove {
    }

    record Verified(boolean passed) implements TaskMove {
    }

    record StopSet(TaskStop stop) implements TaskMove {
    }

    record Lifted() implements TaskMove {
    }

    record StopEnded(TaskStop.Trigger.Ends ends) implements TaskMove {
    }

    record PlanAsked(boolean planMode) implements TaskMove {
    }

    record PlanApproved() implements TaskMove {
    }

    record NoPlanDeclared() implements TaskMove {
    }

    record Autoplanned() implements TaskMove {
    }

    /** A session's report meaning {@code means}; a status that holds its line keeps a report that is not a question. */
    record Said(AgentReport means) implements TaskMove {
    }

    /** A verb's stamp meaning {@code means}, set whatever the status. */
    record Stamped(AgentReport means) implements TaskMove {
    }

    /** A relay handing the turn over; {@code answered} fingerprints what the session had last reported. */
    record Relayed(String step, String digest, String answered) implements TaskMove {
    }

    /** A report, or a refusal, closing the turn it answers. */
    record TurnClosed() implements TaskMove {
    }

    /** A relay that failed to send, put back to the turn it found. */
    record TurnRestored(Turn before) implements TaskMove {
    }
}
