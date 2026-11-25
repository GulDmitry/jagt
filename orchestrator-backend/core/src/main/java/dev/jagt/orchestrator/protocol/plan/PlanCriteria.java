package dev.jagt.orchestrator.protocol.plan;

import dev.jagt.orchestrator.protocol.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The {@code ## Acceptance criteria} section of a plan: one {@code - [tag] text} item per
 * criterion, the tag {@code here}, {@code elsewhere: <part>} or {@code wrong: <why>}, with {@code ,
 * own} appended to one the session wrote itself.
 */
public final class PlanCriteria {

  private static final String FIELD = "plan.md acceptance criteria";
  private static final String HEADING = "## Acceptance criteria";
  private static final Pattern ITEM =
      Pattern.compile("- \\[(here|elsewhere: *[^\\],]+|wrong: *[^\\]]*?\\S)(, *own)?] +\\S.*");

  private PlanCriteria() {}

  public static List<Violation> violationsIn(String plan) {
    List<Violation> violations = new ArrayList<>();
    PlanSection section = PlanSection.under(HEADING, plan);
    List<String> items = section.items();
    for (String line : items) {
      if (!ITEM.matcher(line).matches()) {
        violations.add(
            new Violation(
                FIELD,
                "`"
                    + line
                    + "` carries no tag: write `- [here] <criterion>`,"
                    + " `- [elsewhere: <part>] <criterion>` or `- [wrong: <why>] <criterion>`;"
                    + " add `, own` inside the brackets to one you wrote yourself"));
      }
    }
    if (!section.found()) {
      violations.add(new Violation(FIELD, "add a `" + HEADING + "` section"));
    } else if (items.isEmpty()) {
      violations.add(
          new Violation(
              FIELD, "list at least one criterion under `" + HEADING + "` as a `- ` item"));
    }
    return List.copyOf(violations);
  }
}
