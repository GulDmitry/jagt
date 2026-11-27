package dev.jagt.orchestrator.eval;

import java.util.List;
import java.util.Map;

/**
 * A step told to a session whose commands showed {@code situation}, and the report ending its turn;
 * {@code carried} is what a fresh session is given of the one it replaces, empty for none.
 */
public record SessionTurnCase(
    String name,
    String step,
    String facts,
    String carried,
    String situation,
    String status,
    String outcome) {

  public static List<SessionTurnCase> all() {
    return RoleCases.of("session", SessionTurnCase::parse);
  }

  private static SessionTurnCase parse(String yaml) {
    Map<String, Object> root = Yamls.load(yaml);
    Map<String, Object> expect = Yamls.map(root.get("expect"));
    return new SessionTurnCase(
        Yamls.text(root.get("case")),
        Yamls.text(root.get("step")),
        Yamls.text(root.get("facts")),
        root.get("carried") == null ? "" : Yamls.text(root.get("carried")),
        Yamls.text(root.get("situation")),
        Yamls.text(expect.get("status")),
        Yamls.text(expect.get("outcome")));
  }
}
