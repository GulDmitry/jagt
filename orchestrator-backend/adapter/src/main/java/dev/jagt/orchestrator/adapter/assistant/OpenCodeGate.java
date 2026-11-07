package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.HookEndpoint;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The plugin that puts every call of an OpenCode read to jagt's read gate before it runs, written
 * beside {@code state.json} each start so no repository or worktree holds it.
 */
@Component
class OpenCodeGate {

  static final String URL_VARIABLE = "JAGT_READ_GATE";
  private static final List<String> FILES = List.of("package.json", "index.js");

  private final Path directory;
  private final ReadOnlyTools tools;
  private final HookEndpoint hooks;

  OpenCodeGate(OrchestratorPaths paths, ReadOnlyTools tools, HookEndpoint hooks) {
    this.directory = paths.stateFile().resolveSibling("opencode-gate");
    this.tools = tools;
    this.hooks = hooks;
    write();
  }

  Path directory() {
    return directory;
  }

  Map<String, String> open(String fence, ReadScope scope) {
    tools.open(fence, scope);
    return Map.of(URL_VARIABLE, hooks.readGateUrl(fence));
  }

  boolean refused(String fence) {
    return tools.refused(fence);
  }

  int heardCalls(String fence) {
    return tools.calls(fence).size();
  }

  /**
   * Whether any call of the run reached the gate: none having done so while one ran means the
   * plugin never loaded.
   */
  boolean heard(String fence) {
    return heardCalls(fence) > 0;
  }

  void close(String fence) {
    tools.lift(fence);
  }

  private void write() {
    try {
      Files.createDirectories(directory);
      for (String file : FILES) {
        Path staged = Files.createTempFile(directory, file, ".part");
        try (InputStream in =
            new ClassPathResource("opencode/jagt-gate/" + file).getInputStream()) {
          Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(staged, directory.resolve(file), StandardCopyOption.ATOMIC_MOVE);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("cannot write the opencode gate plugin to " + directory, e);
    }
  }
}
