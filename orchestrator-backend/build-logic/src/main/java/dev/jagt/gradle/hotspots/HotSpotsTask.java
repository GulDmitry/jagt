package dev.jagt.gradle.hotspots;

import dev.jagt.gradle.exec.Git;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

/** Prints the ten classes the last 30 days of fixes return to most and fails past the ceiling. */
public abstract class HotSpotsTask extends DefaultTask {

  @Internal
  public abstract DirectoryProperty getRepository();

  @Input
  public abstract Property<Integer> getMax();

  @TaskAction
  void verify() {
    Path root = getRepository().get().getAsFile().toPath().getParent();
    List<HotSpots.Spot> top = new HotSpots(root, new Git(root), Clock.systemUTC()).top();
    top.forEach(spot -> getLogger().quiet(spot.line()));
    HotSpots.enforce(top, getMax().get());
  }
}
