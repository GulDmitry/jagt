package dev.jagt.orchestrator.flow.role;

import java.util.ArrayList;
import java.util.List;

/** Every point there is: the built-in ones and those the given roles declare. */
public final class Points {

  private static final List<Point> BUILT_IN =
      List.of(
          Point.YOUR_MOVE,
          Point.STOPPED,
          Point.AUTO_REVIEW,
          Point.CONFLICT_STALLED,
          Point.REQUEST_OPENED,
          Point.CHECKS_GREEN,
          Point.CHECKS_FAILED,
          Point.APPROVED,
          Point.DEPLOY_PRESSED,
          Point.DEPLOYED,
          Point.DEPLOY_CONFLICT,
          Point.REVERTED,
          Point.MERGED,
          Point.DONE,
          Point.MASTER_SEALED,
          Point.MASTER_ASKED_STOP,
          Point.MASTER_LEFT,
          Point.MASTER_UNANSWERED,
          Point.MASTER_PRESSED_DEPLOY,
          Point.MASTER_SHIPPED_COMMENTED,
          Point.HOUSEKEEPING,
          Point.INSTALL,
          Point.USAGE);

  private Points() {}

  public static List<Point> builtIn() {
    return BUILT_IN;
  }

  public static List<Point> all(List<CheckerRole> roles) {
    List<Point> all = new ArrayList<>(BUILT_IN);
    for (CheckerRole role : roles) {
      role.points().forEach(kind -> all.add(Point.of(role.id(), kind)));
    }
    return List.copyOf(all);
  }
}
