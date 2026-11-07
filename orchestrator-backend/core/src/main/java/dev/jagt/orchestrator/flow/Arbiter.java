package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.MasterRight;
import java.util.EnumSet;
import java.util.Set;

/**
 * The one role that stands where the human stands: its id, the window its steps are fed to, and
 * what it may press.
 */
public final class Arbiter {

  public static final String ID = "master";

  public static final String WINDOW = "master";

  /** {@code mine} are the steps the human keeps even where the Master acts. */
  public record Profile(MasterMode mode, Set<MasterRight> mine) {

    public static final Profile OFF = new Profile(MasterMode.OFF, Set.of());

    public Profile {
      mine = mine.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(mine));
    }

    public boolean running() {
      return mode != MasterMode.OFF;
    }

    public boolean may(MasterRight right) {
      return mode == MasterMode.ACT && !mine.contains(right);
    }

    /**
     * This profile where the Master stands in, none where it read the round or left the task to the
     * human.
     */
    public Profile at(MasterPhase phase) {
      return phase == MasterPhase.STANDS_IN ? this : OFF;
    }
  }

  private Arbiter() {}
}
