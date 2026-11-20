package fitness.scan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

final class Tracked {

  private Tracked() {}

  /** Every text file the repository holding the root tracks, as paths below the root's own spelling. */
  static List<Path> files(Path root) {
    try {
      Path top = Path.of(git(root, "rev-parse", "--show-toplevel").strip()).toRealPath();
      Path base = root.resolve(root.toRealPath().relativize(top)).normalize();
      return Arrays.stream(git(top, "ls-files", "-z").split("\0"))
          .filter(name -> !name.isEmpty())
          .map(base::resolve)
          .filter(Files::isRegularFile)
          .filter(Tracked::isText)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean isText(Path file) {
    try {
      return !new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).contains("\0");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String git(Path directory, String... arguments) throws IOException {
    String[] command = new String[arguments.length + 3];
    command[0] = "git";
    command[1] = "-C";
    command[2] = directory.toString();
    System.arraycopy(arguments, 0, command, 3, arguments.length);
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    try {
      if (process.waitFor() != 0) {
        throw new IllegalStateException("git failed in " + directory + ": " + output);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted running git in " + directory, e);
    }
    return output;
  }
}
