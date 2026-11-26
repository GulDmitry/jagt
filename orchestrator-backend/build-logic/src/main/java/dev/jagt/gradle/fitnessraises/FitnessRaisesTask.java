package dev.jagt.gradle.fitnessraises;

import dev.jagt.gradle.exec.Git;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

/** Fails for each fitness row loosened since the base branch's merge base without a decision. */
public abstract class FitnessRaisesTask extends DefaultTask {

  @Internal
  public abstract DirectoryProperty getRepository();

  @Input
  public abstract Property<String> getBase();

  @TaskAction
  void verify() throws IOException {
    Path root = getRepository().get().getAsFile().toPath().getParent();
    Git git = new Git(root);
    String mergeBase = mergeBase(git);
    List<String> changed =
        new ArrayList<>(
            git.lines(
                "diff", "--name-only", "--diff-filter=AMR", mergeBase, "--", "docs/decisions"));
    changed.addAll(git.lines("ls-files", "--others", "--exclude-standard", "--", "docs/decisions"));
    List<String> violations = new ArrayList<>();
    for (String file : git.lines("ls-files", "--", "fitness.yml", "**/fitness.yml")) {
      List<String> baseLines = showAt(git, mergeBase, file);
      if (!baseLines.isEmpty()) {
        violations.addAll(
            FitnessRaises.loosened(
                file,
                String.join("\n", baseLines),
                Files.readString(root.resolve(file)),
                FitnessRaises.recordChangedIn(changed)));
      }
    }
    if (!violations.isEmpty()) {
      throw new GradleException(String.join(System.lineSeparator(), violations));
    }
  }

  private String mergeBase(Git git) {
    try {
      return git.lines("merge-base", "origin/" + getBase().get(), "HEAD").getFirst();
    } catch (IllegalStateException noRemoteBranch) {
      return git.lines("merge-base", getBase().get(), "HEAD").getFirst();
    }
  }

  private static List<String> showAt(Git git, String commit, String file) {
    try {
      return git.lines("show", commit + ":" + file);
    } catch (IllegalStateException absentInBase) {
      return List.of();
    }
  }
}
