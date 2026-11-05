package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;

/** Whose a standing stop is to lift: nobody's where none stands, the Master's until it was asked or its bound is spent, else the human's. */
public enum StopHold {
    NONE, HUMAN, MASTER;

    /** Stops the Master lifts between two lifts of the human's. */
    public static final int LIFTS_PER_HUMAN_LIFT = 1;

    public static StopHold of(TaskState task, Arbiter.Profile profile) {
        TaskStop stop = task.stop();
        if (stop == null) {
            return NONE;
        }
        return mayBeLifted(task) && profile.may(MasterRight.LIFT) && !asked(task) ? MASTER : HUMAN;
    }

    /** A stop of a trigger the Master decides, while its bound is unspent. */
    public static boolean mayBeLifted(TaskState task) {
        TaskStop stop = task.stop();
        return stop != null && FlowRules.liftedByTheMaster(stop.trigger())
                && task.masterOrNone().liftedStops() < LIFTS_PER_HUMAN_LIFT;
    }

    /** The Master was asked about the stop standing, so it is not asked again. */
    public static boolean asked(TaskState task) {
        TaskStop stop = task.stop();
        return stop != null && task.masterOrNone().askedStop().equals(stop.key());
    }
}
