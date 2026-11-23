package dev.jagt.gradle.divergence;

import dev.jagt.gradle.exec.Commands;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

/** Fails with one line per red CI run of a locally green tree that docs/divergences.md lacks. */
public abstract class DivergenceRegisterTask extends DefaultTask {

  @Internal
  public abstract DirectoryProperty getRepository();

  @Inject
  protected abstract ExecOperations getExecOperations();

  @TaskAction
  void verify() throws IOException {
    Path root = getRepository().get().getAsFile().toPath().getParent();
    String gitDir = run(root, "git", "rev-parse", "--path-format=absolute", "--git-common-dir");
    Path greensFile = Path.of(gitDir.strip()).resolve("ci-local.greens");
    Set<String> greens =
        Files.isRegularFile(greensFile) ? new HashSet<>(Files.readAllLines(greensFile)) : Set.of();
    String listing =
        run(
            root,
            "gh",
            "run",
            "list",
            "--limit",
            "100",
            "--json",
            "databaseId,headSha,conclusion,event",
            "--jq",
            ".[] | [.databaseId, .headSha, .conclusion, .event] | @tsv");
    List<String> missing =
        Divergences.unregistered(
            listing.lines().toList(),
            sha -> tree(root, sha),
            greens,
            Files.readString(root.resolve("docs/divergences.md")));
    if (!missing.isEmpty()) {
      throw new GradleException(String.join(System.lineSeparator(), missing));
    }
  }

  private String tree(Path root, String sha) {
    try {
      return run(root, "git", "rev-parse", sha + "^{tree}").strip();
    } catch (GradleException unknownCommit) {
      return "";
    }
  }

  private String run(Path directory, String... command) {
    return Commands.output(getExecOperations(), directory, command)
        .orElseThrow(() -> new GradleException("failed: " + String.join(" ", command)));
  }
}
