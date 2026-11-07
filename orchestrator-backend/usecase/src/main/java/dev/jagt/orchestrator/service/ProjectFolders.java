package dev.jagt.orchestrator.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ProjectFolders {

  private static final int LIMIT = 50;

  public record Folder(String name, boolean repository) {}

  /**
   * The folders under the last complete folder of {@code typed} whose name starts with what follows
   * it, repositories first.
   */
  public List<Folder> under(String typed) {
    String expanded = ProjectSetup.expand(typed);
    int slash = expanded.lastIndexOf('/');
    if (slash < 0) {
      return List.of();
    }
    String prefix = expanded.substring(slash + 1).toLowerCase(Locale.ROOT);
    try (Stream<Path> children = Files.list(Path.of(expanded.substring(0, slash + 1)))) {
      return children
          .filter(Files::isDirectory)
          .map(
              child ->
                  new Folder(child.getFileName().toString(), Files.exists(child.resolve(".git"))))
          .filter(folder -> folder.name().toLowerCase(Locale.ROOT).startsWith(prefix))
          .filter(folder -> prefix.startsWith(".") || !folder.name().startsWith("."))
          .sorted(
              Comparator.comparing((Folder folder) -> !folder.repository())
                  .thenComparing(folder -> folder.name().toLowerCase(Locale.ROOT))
                  .thenComparing(Folder::name))
          .limit(LIMIT)
          .toList();
    } catch (IOException | InvalidPathException unreadable) {
      log.atDebug()
          .setMessage("folders not listed")
          .addKeyValue("path", typed)
          .addKeyValue("cause", unreadable.toString())
          .log();
      return List.of();
    }
  }
}
