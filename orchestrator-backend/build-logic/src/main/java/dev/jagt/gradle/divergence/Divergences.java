package dev.jagt.gradle.divergence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Names each red run of a recorded-green tree that the divergence register has no row for. */
public final class Divergences {

  private static final Set<String> RED = Set.of("failure", "timed_out");

  private Divergences() {}

  /**
   * @param runs tab-separated lines: run id, head sha, conclusion, event
   * @param treeOf the tree of a head sha, empty when the commit is unknown here
   * @param greens the trees a local gate recorded green
   * @param register the register's text, a table with the run id in its second cell
   */
  public static List<String> unregistered(
      List<String> runs, UnaryOperator<String> treeOf, Set<String> greens, String register) {
    Set<String> rows = runIds(register);
    List<String> missing = new ArrayList<>();
    for (String line : runs) {
      String[] cells = line.split("\t");
      if (cells.length < 4 || !RED.contains(cells[2]) || "schedule".equals(cells[3])) {
        continue;
      }
      String tree = treeOf.apply(cells[1]);
      if (greens.contains(tree) && !rows.contains(cells[0])) {
        missing.add("unregistered: run " + cells[0] + " is red on the recorded-green tree " + tree);
      }
    }
    return missing;
  }

  private static Set<String> runIds(String register) {
    Set<String> ids = new HashSet<>();
    for (String line : register.split("\r?\n")) {
      String[] cells = line.split("\\|");
      if (line.startsWith("|") && cells.length > 2) {
        ids.add(cells[2].strip());
      }
    }
    return ids;
  }
}
