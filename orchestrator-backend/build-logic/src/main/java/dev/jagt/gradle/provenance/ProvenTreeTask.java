package dev.jagt.gradle.provenance;

import dev.jagt.gradle.exec.Commands;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

/** Fails unless the commit is a merge whose tree equals its second parent's, green in CI. */
public abstract class ProvenTreeTask extends DefaultTask {

  @Internal
  public abstract DirectoryProperty getRepository();

  @Input
  public abstract Property<String> getCommit();

  @Inject
  protected abstract ExecOperations getExecOperations();

  @TaskAction
  void verify() {
    Path root = getRepository().get().getAsFile().toPath().getParent();
    String commit = getCommit().get();
    String head = output(root, "git", "rev-parse", commit + "^2").strip();
    String headTree =
        head.isEmpty() ? "" : output(root, "git", "rev-parse", head + "^{tree}").strip();
    List<String> conclusions = head.isEmpty() ? List.of() : conclusions(root, head);
    String mergeTree = output(root, "git", "rev-parse", commit + "^{tree}").strip();
    Optional<String> refusal = ProvenTree.refusal(mergeTree, headTree, conclusions);
    if (refusal.isPresent()) {
      throw new GradleException("nothing is published: " + refusal.get());
    }
  }

  private List<String> conclusions(Path root, String head) {
    return output(
            root,
            "gh",
            "run",
            "list",
            "--workflow",
            "ci.yml",
            "--event",
            "pull_request",
            "--commit",
            head,
            "--json",
            "conclusion",
            "--jq",
            ".[].conclusion")
        .lines()
        .toList();
  }

  private String output(Path directory, String... command) {
    return Commands.output(getExecOperations(), directory, command).orElse("");
  }
}
