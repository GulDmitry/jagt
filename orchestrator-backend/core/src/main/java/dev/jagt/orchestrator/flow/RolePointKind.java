package dev.jagt.orchestrator.flow;

import java.util.Arrays;
import java.util.Optional;

/** The points a checker role may declare; each stands as {@code <role>-<kind>}. */
public enum RolePointKind {
  PASSED("passed", "the role's check passed on the sha"),
  ROUND_SENT("round-sent", "the role's findings went to the session as a round"),
  DECLINED("declined", "the role declined the check");

  private final String id;
  private final String stands;

  RolePointKind(String id, String stands) {
    this.id = id;
    this.stands = stands;
  }

  public String id() {
    return id;
  }

  String stands() {
    return stands;
  }

  public static Optional<RolePointKind> ofId(String id) {
    return Arrays.stream(values()).filter(kind -> kind.id.equals(id)).findFirst();
  }
}
