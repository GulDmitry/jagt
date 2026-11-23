package dev.jagt.gradle.testsfor;

import java.util.Arrays;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.Option;

@UntrackedTask(because = "reads the working tree and prints a selection")
public abstract class TestsForTask extends DefaultTask {

  @Internal
  public abstract DirectoryProperty getBackend();

  @Input
  @Optional
  @Option(option = "paths", description = "Comma-separated changed paths; none means this branch.")
  public abstract Property<String> getPaths();

  @Input
  @Option(option = "base", description = "The branch the change is measured against.")
  public abstract Property<String> getBase();

  @TaskAction
  void print() {
    TestsFor testsFor = TestsFor.in(getBackend().get().getAsFile().toPath());
    List<String> paths =
        getPaths().isPresent()
            ? Arrays.stream(getPaths().get().split(",")).map(testsFor::stripRoot).toList()
            : testsFor.changedAgainst(getBase().get());
    TestsFor.Plan plan = testsFor.plan(paths);
    plan.dropped().forEach(note -> getLogger().error(note));
    plan.lines().forEach(line -> getLogger().quiet(line));
  }
}
