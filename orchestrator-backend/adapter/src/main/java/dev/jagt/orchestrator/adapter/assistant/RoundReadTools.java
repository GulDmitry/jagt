package dev.jagt.orchestrator.adapter.assistant;

import java.util.List;

final class RoundReadTools {

  /** The fence on the run is what the human's own allow rules cannot widen. */
  static final List<String> SHELL =
      List.of(
          "git diff*",
          "git log*",
          "git show*",
          "git status*",
          "git blame*",
          "git merge-base*",
          "git rev-parse*",
          "git ls-files*",
          "git -C*");

  /**
   * The human's own allow rules still load with their MCP servers, so what must never run is denied
   * as well. Git takes {@code --ou} for {@code --output}, which writes a file, and {@code -c} for
   * any config, an external diff included.
   */
  static final List<String> REFUSED_SHELL =
      List.of(
          "git push*",
          "git commit*",
          "git reset*",
          "git checkout*",
          "git stash*",
          "git restore*",
          "git clean*",
          "git * --ou*",
          "git -c*",
          "git --config-env*");

  static final List<String> REFUSED_READ = List.of("**/review_replies.md");

  /**
   * The tests ran before the round reached review; every role building at once in one worktree is
   * the cost.
   */
  static final List<String> BUILDS =
      List.of(
          "./gradlew*",
          "gradle*",
          "./mvnw*",
          "mvn*",
          "npm*",
          "npx*",
          "yarn*",
          "pnpm*",
          "composer*",
          "vendor/bin/phpunit*",
          "pytest*",
          "make*",
          "docker*",
          "timeout*");

  private RoundReadTools() {}
}
