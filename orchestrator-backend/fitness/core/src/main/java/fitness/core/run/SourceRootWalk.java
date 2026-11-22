package fitness.core.run;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** A file deleted mid-walk, a JVM's attach file among them, is passed over rather than failing it. */
final class SourceRootWalk extends SimpleFileVisitor<Path> {

  private final Predicate<Path> isRoot;
  private final List<Path> roots = new ArrayList<>();

  SourceRootWalk(Predicate<Path> isRoot) {
    this.isRoot = isRoot;
  }

  List<Path> roots() {
    return List.copyOf(roots);
  }

  @Override
  public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
    collect(dir, attrs);
    return FileVisitResult.CONTINUE;
  }

  @Override
  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
    collect(file, attrs);
    return FileVisitResult.CONTINUE;
  }

  @Override
  public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
    if (e instanceof NoSuchFileException) {
      return FileVisitResult.CONTINUE;
    }
    throw e;
  }

  private void collect(Path path, BasicFileAttributes attrs) {
    if (attrs.isDirectory() && isRoot.test(path)) {
      roots.add(path);
    }
  }
}
