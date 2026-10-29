package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.With;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * A {@code do}, however it arrived. {@code ref} is an issue key or a URL to the item in any tracker, null when
 * the human wrote the task themselves and {@code notes} holds it; {@code baseBranch} is what to cut from and to
 * target with the review request, null = the project's own.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@With
public record LaunchRequest(@Nullable String ref, @Nullable String project, @Nullable String mode,
                            @Nullable String strategy, @Nullable String baseBranch, @Nullable String notes) {

    public static final String GRAMMAR = "do <ticket|url> [project[,project…]] [plan] [from <branch>] [notes…]";
    /** No tracker item: the project comes first and the rest is the task itself. */
    public static final String OWN_GRAMMAR = "do <project[,project…]> <what to do…> [plan] [from <branch>]";

    public static LaunchRequest defaults() {
        return new LaunchRequest(null, null, null, null, null, null);
    }

    public static LaunchRequest of(String ref) {
        return defaults().withRef(ref);
    }

    /**
     * One typed line. {@code plan}, a configured project key, a branch strategy and {@code from <branch>} are
     * consumed as modifiers in any order; the rest is notes. Each is recognised only as a LEADING token, so a
     * note may contain the word "plan". A line OPENING on a project key names no item: a mistyped key is not one
     * either, so it still goes to the tracker and comes back as no such item rather than as a task.
     */
    public static LaunchRequest ofLine(String line, Set<String> projectKeys) {
        List<String> tail = new ArrayList<>(Arrays.asList((line == null ? "" : line.strip()).split("\\s+")));
        if (tail.isEmpty() || tail.get(0).isBlank()) {
            throw new IllegalArgumentException("usage: " + GRAMMAR);
        }
        String ref = isProjects(tail.get(0), projectKeys) ? null : tail.remove(0);
        LaunchRequest request = defaults().withRef(ref);
        for (LaunchRequest next = consumeModifier(request, tail, projectKeys); next != null;
             next = consumeModifier(request, tail, projectKeys)) {
            request = next;
            tail.remove(0);
        }
        return request.withNotes(String.join(" ", tail).strip()).normalized();
    }

    private static @Nullable LaunchRequest consumeModifier(LaunchRequest request, List<String> tail,
                                                           Set<String> projectKeys) {
        if (tail.isEmpty()) {
            return null;
        }
        String head = tail.get(0);
        if (request.mode() == null && head.equals("plan")) {
            return request.withMode("plan");
        }
        if (request.project() == null && isProjects(head, projectKeys)) {
            return request.withProject(head);
        }
        if (request.strategy() == null && BranchStrategy.ids().contains(head)) {
            return request.withStrategy(head);
        }
        if (request.baseBranch() == null && head.equals("from")) {
            if (tail.size() < 2 || tail.get(1).isBlank()) {
                throw new IllegalArgumentException("usage: " + GRAMMAR
                        + " — `from` needs the branch to start from");
            }
            return request.withBaseBranch(tail.remove(1));
        }
        return null;
    }

    /** Every comma-separated key must be configured, so a note that happens to hold a comma stays a note. */
    private static boolean isProjects(String token, Set<String> known) {
        List<String> named = Arrays.stream(token.split(",")).map(String::strip)
                .filter(key -> !key.isEmpty()).toList();
        return !named.isEmpty() && known.containsAll(named);
    }

    /** Blanks become nulls: a surface that posts "" and one that omits the token must look the same. */
    public LaunchRequest normalized() {
        return new LaunchRequest(blankToNull(ref), blankToNull(project), blankToNull(mode),
                blankToNull(strategy), blankToNull(baseBranch), blankToNull(notes));
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
