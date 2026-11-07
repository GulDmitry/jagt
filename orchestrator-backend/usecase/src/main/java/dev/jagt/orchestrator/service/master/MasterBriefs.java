package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.RoleFiles;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.Step;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** The two briefs a Master reader judges by: its own, and the one the session worked to. */
@Component
@RequiredArgsConstructor
@Slf4j
public class MasterBriefs {

  private final OrchestratorPaths paths;
  private final SessionBrief sessionBrief;

  public Optional<String> master(String taskId, ConfigService.ConfigFile.MasterConfig config) {
    try {
      return Optional.of(Files.readString(file(config)));
    } catch (IOException unreadable) {
      log.atError()
          .setMessage("master brief unreadable")
          .addKeyValue("task", taskId)
          .addKeyValue("file", config.briefOrDefault())
          .addKeyValue("cause", unreadable.toString())
          .log();
      return Optional.empty();
    }
  }

  /**
   * Where none was named, the first copy that exists, the shipped one last: judging by default asks
   * for no setup.
   */
  public Path file(ConfigService.ConfigFile.MasterConfig config) {
    boolean unnamed = config.brief() == null || config.brief().isBlank();
    Path named = paths.root().resolve(config.briefOrDefault());
    return unnamed ? new RoleFiles(paths, "master").briefFile().orElse(named) : named;
  }

  public String author() {
    return sessionBrief.section(Step.CONTEXT).orElse("");
  }
}
