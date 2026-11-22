package fitness.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

/** Every file of the repository holding the directory that git tracks and the disk still has. */
public abstract class TrackedFiles implements ValueSource<List<File>, TrackedFiles.Parameters> {

  /** The directory inside the repository. */
  public interface Parameters extends ValueSourceParameters {
    DirectoryProperty getDirectory();
  }

  @Override
  public List<File> obtain() {
    Path directory = getParameters().getDirectory().get().getAsFile().toPath();
    try {
      Process process =
          new ProcessBuilder("git", "-C", directory.toString(), "ls-files", "-z", ":/")
              .redirectErrorStream(true)
              .start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exit = process.waitFor();
      if (exit != 0) {
        throw new ToolFailureException("git", exit, output.strip());
      }
      return Arrays.stream(output.split("\0"))
          .filter(name -> !name.isEmpty())
          .map(name -> directory.resolve(name).normalize())
          .filter(Files::isRegularFile)
          .map(Path::toFile)
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("git did not start in " + directory, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted running git in " + directory, e);
    }
  }
}
