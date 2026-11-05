package dev.jagt.orchestrator.adapter.assistant;

import java.util.List;

final class RoundReadTools {

    /** The CLI's own allow-list; the fence on the run is what the human's allow rules cannot widen. */
    static final List<String> READS = List.of("Read", "Grep", "Glob", "Bash(git diff:*)",
            "Bash(git log:*)", "Bash(git show:*)", "Bash(git status:*)", "Bash(git blame:*)",
            "Bash(git merge-base:*)", "Bash(git rev-parse:*)", "Bash(git ls-files:*)", "Bash(git -C:*)");
    /**
     * The human's own allow rules still load with their MCP servers, so what must never run is denied as well.
     * Git takes {@code --ou} for {@code --output}, which writes a file, and {@code -c} for any config, an external
     * diff included.
     */
    static final List<String> REFUSED = List.of("Edit", "Write", "NotebookEdit",
            "Bash(git push:*)", "Bash(git commit:*)", "Bash(git reset:*)", "Bash(git checkout:*)",
            "Bash(git stash:*)", "Bash(git restore:*)", "Bash(git clean:*)", "Bash(git * --ou*)",
            "Bash(git -c:*)", "Bash(git --config-env:*)", "Read(**/review_replies.md)");
    /** The tests ran before the round reached review; every role building at once in one worktree is the cost. */
    static final List<String> BUILDS = List.of("Bash(./gradlew:*)", "Bash(gradle:*)", "Bash(./mvnw:*)",
            "Bash(mvn:*)", "Bash(npm:*)", "Bash(npx:*)", "Bash(yarn:*)", "Bash(pnpm:*)", "Bash(composer:*)",
            "Bash(vendor/bin/phpunit:*)", "Bash(pytest:*)", "Bash(make:*)", "Bash(docker:*)", "Bash(timeout:*)");

    private RoundReadTools() {
    }
}
