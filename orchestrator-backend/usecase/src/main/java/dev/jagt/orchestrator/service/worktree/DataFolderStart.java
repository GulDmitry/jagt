package dev.jagt.orchestrator.service.worktree;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Creates the data folder and brings a state kept in the old place into it, once. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataFolderStart {

  private final OrchestratorPaths paths;

  @EventListener(ContextRefreshedEvent.class)
  public void prepare() {
    try {
      Files.createDirectories(paths.data());
    } catch (IOException e) {
      log.atWarn()
          .setMessage("data folder not created")
          .addKeyValue("path", paths.data())
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "the start check names it")
          .log();
      return;
    }
    Path old = paths.legacyStateFile();
    Path state = paths.stateFile();
    if (Files.isRegularFile(old) && !Files.exists(state) && !old.equals(state)) {
      move(old, state);
      Path backup = old.resolveSibling(old.getFileName() + ".bak");
      if (Files.isRegularFile(backup)) {
        move(backup, state.resolveSibling(backup.getFileName().toString()));
      }
    }
  }

  private static void move(Path from, Path to) {
    try {
      Files.createDirectories(to.getParent());
      Files.move(from, to);
      log.atInfo()
          .setMessage("state moved into the data folder")
          .addKeyValue("from", from)
          .addKeyValue("to", to)
          .log();
    } catch (IOException e) {
      log.atWarn()
          .setMessage("state not moved")
          .addKeyValue("from", from)
          .addKeyValue("to", to)
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "the old file stays and the tasks start empty")
          .log();
    }
  }
}
