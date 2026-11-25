package dev.jagt.orchestrator.flow.role;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.master.MasterMode;
import dev.jagt.orchestrator.task.master.MasterRight;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The one role that stands where the human stands: its id and what it may press. */
public final class Arbiter {

  public static final String ID = "master";

  /** A verb the word of a {@link MasterRight} keeps as well. */
  private static final Map<TaskAction, MasterRight> STEPS =
      Map.of(TaskAction.ADD_REPO, MasterRight.REPO);

  /** {@code mine} are the words the human keeps even where the Master acts: steps and verb ids. */
  public record Profile(MasterMode mode, Set<String> mine) {

    public static final Profile OFF = new Profile(MasterMode.OFF, Set.of());

    public Profile {
      mine = Set.copyOf(mine);
    }

    public boolean running() {
      return mode != MasterMode.OFF;
    }

    public boolean may(MasterRight right) {
      return mode == MasterMode.ACT && !mine.contains(right.id());
    }

    public boolean keeps(TaskAction action, TaskState task) {
      return mine.contains(action.id())
          || step(action, task).map(right -> mine.contains(right.id())).orElse(false);
    }

    public boolean keeps(String verb) {
      return mine.contains(verb.strip().toLowerCase(Locale.ROOT));
    }

    /** Whether the Master may approve the plan that waits on {@code task}. */
    public boolean mayApprovePlan(TaskState task) {
      return may(planRight(task));
    }

    private static Optional<MasterRight> step(TaskAction action, TaskState task) {
      return action == TaskAction.APPROVE_PLAN
          ? Optional.of(planRight(task))
          : Optional.ofNullable(STEPS.get(action));
    }

    private static MasterRight planRight(TaskState task) {
      return task.gate().planApproved() ? MasterRight.REPLAN : MasterRight.PLAN;
    }

    /**
     * This profile where the Master stands in, none where it read the round or left the task to the
     * human; what it keeps stays kept.
     */
    public Profile at(MasterPhase phase) {
      return phase == MasterPhase.STANDS_IN ? this : new Profile(MasterMode.OFF, mine);
    }

    public Profile at(TaskState task) {
      return at(MasterPhase.of(task));
    }

    /** The Master runs and has read the plan or round in front. */
    public boolean reading(TaskState task) {
      return running() && MasterPhase.readTheRoundInFront(task);
    }
  }

  private Arbiter() {}
}
