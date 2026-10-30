package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;

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
}
