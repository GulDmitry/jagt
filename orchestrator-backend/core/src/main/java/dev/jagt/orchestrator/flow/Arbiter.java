package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.MasterRight;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The one role that stands where the human stands: its id, the window its steps are fed to, and
 * what it may press.
 */
public final class Arbiter {

  public static final String ID = "master";

  public static final String WINDOW = "master";

  /** A verb the word of a {@link MasterRight} keeps as well. */
  private static final Map<TaskAction, MasterRight> STEPS =
      Map.of(TaskAction.APPROVE_PLAN, MasterRight.PLAN, TaskAction.ADD_REPO, MasterRight.REPO);

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

    public boolean keeps(TaskAction action) {
      return mine.contains(action.id())
          || (STEPS.containsKey(action) && mine.contains(STEPS.get(action).id()));
    }

    public boolean keeps(String verb) {
      return mine.contains(verb.strip().toLowerCase(Locale.ROOT));
    }

    /**
     * This profile where the Master stands in, none where it read the round or left the task to the
     * human; what it keeps stays kept.
     */
    public Profile at(MasterPhase phase) {
      return phase == MasterPhase.STANDS_IN ? this : new Profile(MasterMode.OFF, mine);
    }
  }

  private Arbiter() {}
}
