package dev.jagt.orchestrator.protocol;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code ## Repositories} section of a plan for a task with several repositories: one
 * {@code - [changed] <key>} or {@code - [unaffected: <why>] <key>} item per repository of the task.
 */
public final class PlanRepositories {

    private static final String FIELD = "plan.md repositories";
    private static final String HEADING = "## Repositories";
    private static final Pattern ITEM = Pattern.compile("- \\[(?:changed|unaffected: *[^\\]]*?\\S)] +(\\S+)\\s*");

    private PlanRepositories() {
    }

    public static List<Violation> violationsIn(String plan, List<String> projects) {
        if (projects.size() < 2) {
            return List.of();
        }
        List<Violation> violations = new ArrayList<>();
        Set<String> listed = new LinkedHashSet<>();
        boolean inSection = false;
        boolean found = false;
        for (String line : plan.lines().map(String::strip).toList()) {
            if (line.startsWith("## ")) {
                inSection = line.equalsIgnoreCase(HEADING);
                found |= inSection;
            } else if (inSection && line.startsWith("- ")) {
                itemViolation(line, projects, listed).ifPresent(violations::add);
            }
        }
        if (!found) {
            violations.add(new Violation(FIELD, "add a `" + HEADING + "` section with one `- [changed] <key>` or"
                    + " `- [unaffected: <why>] <key>` item per repository: " + String.join(", ", projects)));
        } else {
            projects.stream().filter(project -> !listed.contains(project)).forEach(project ->
                    violations.add(new Violation(FIELD, "list `" + project + "` under `" + HEADING
                            + "` as `- [changed] " + project + "` or `- [unaffected: <why>] " + project + "`")));
        }
        return List.copyOf(violations);
    }

    private static Optional<Violation> itemViolation(String line, List<String> projects, Set<String> listed) {
        Matcher item = ITEM.matcher(line);
        if (!item.matches()) {
            return Optional.of(new Violation(FIELD, "`" + line + "` carries no tag: write `- [changed] <key>`"
                    + " or `- [unaffected: <why>] <key>`"));
        }
        String key = item.group(1);
        if (!projects.contains(key)) {
            return Optional.of(new Violation(FIELD, "`" + key + "` is no repository of this task: list only "
                    + String.join(", ", projects)));
        }
        listed.add(key);
        return Optional.empty();
    }
}
