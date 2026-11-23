package dev.jagt.gradle.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

/** Builds each base image for amd64 and scans it as the registry workflow's per-image step does. */
public abstract class BaseImageScanTask extends DefaultTask {

  @Internal
  public abstract DirectoryProperty getRepository();

  @Input
  public abstract Property<Boolean> getNeeded();

  @Inject
  protected abstract ExecOperations getExecOperations();

  @Inject
  protected abstract FileSystemOperations getFileSystemOperations();

  @TaskAction
  void scan() throws IOException {
    if (!getNeeded().get()) {
      getLogger()
          .lifecycle("base images not scanned: nothing under base/ changed against the base");
      return;
    }
    Path repository = getRepository().get().getAsFile().toPath();
    Path archives = Files.createTempDirectory("base-image-scan");
    List<String> failed = new ArrayList<>();
    try {
      for (BaseImages.Image image : BaseImages.all()) {
        Path archive = archives.resolve(image.name() + ".tar");
        require(BaseImages.build(repository, image));
        require(BaseImages.save(image, archive));
        if (run(BaseImages.scan(repository, archive)) != 0) {
          failed.add(image.name());
        }
      }
    } finally {
      for (BaseImages.Image image : BaseImages.all()) {
        run(List.of("docker", "rmi", "--force", BaseImages.tag(image)));
      }
      getFileSystemOperations().delete(spec -> spec.delete(archives.toFile()));
    }
    if (!failed.isEmpty()) {
      throw new GradleException("base images with a vulnerability that has a fix: " + failed);
    }
  }

  private void require(List<String> command) {
    int exit = run(command);
    if (exit != 0) {
      throw new GradleException("exit " + exit + ": " + String.join(" ", command));
    }
  }

  private int run(List<String> command) {
    return getExecOperations()
        .exec(
            spec -> {
              spec.commandLine(command);
              spec.setIgnoreExitValue(true);
            })
        .getExitValue();
  }
}
