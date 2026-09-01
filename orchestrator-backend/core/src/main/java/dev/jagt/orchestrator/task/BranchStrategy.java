package dev.jagt.orchestrator.task;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What a launch does about a branch of this name that already exists. The words and what each one means live
 * here alone: a surface offering the choice renders these hints rather than a copy of them.
 */
public enum BranchStrategy {

    FRESH("stop if the branch exists"),
    RECREATE("delete the branch, start over from the base"),
    RESUME("continue the branch and its commits");

    private final String hint;

    BranchStrategy(String hint) {
        this.hint = hint;
    }

    /** The spelling every surface accepts and shows. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String hint() {
        return hint;
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(BranchStrategy::id).toList();
    }

    /** What a surface offering the choice renders. */
    public static List<Choice> choices() {
        return Arrays.stream(values()).map(s -> new Choice(s.id(), s.hint)).toList();
    }

    public record Choice(String id, String hint) {
    }

    /** What a launch that named nothing gets. */
    public static BranchStrategy of(String value) {
        if (value == null || value.isBlank()) {
            return FRESH;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown branchStrategy '" + value + "'. Allowed: "
                    + String.join(", ", ids()));
        }
    }
}
