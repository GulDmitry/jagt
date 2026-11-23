package dev.jagt.gradle.guards;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

/** Fails with one line per reason a guard script breaks its contract, lints red or fails a case. */
public abstract class GuardsGateTask extends DefaultTask {

  @Internal
  public abstract DirectoryProperty getRepository();

  @Inject
  protected abstract ExecOperations getExecOperations();

  @TaskAction
  void verify() throws IOException {
    Path root = getRepository().get().getAsFile().toPath();
    List<String> problems = new ArrayList<>(GuardsGate.problems(root));
    for (Path script : GuardsGate.scripts(root)) {
      String relative = root.relativize(script).toString();
      boolean zsh = Files.readAllLines(script).stream().findFirst().orElse("").contains("zsh");
      List<String> lint =
          zsh ? List.of("zsh", "-n", relative) : List.of("shellcheck", "-S", "warning", relative);
      if (run(root, lint, false) != 0) {
        problems.add((zsh ? "zsh -n red: " : "shellcheck red: ") + relative);
      }
    }
    if (run(root, List.of("bats", "scripts/guards/test"), true) != 0) {
      problems.add("bats red: scripts/guards/test");
    }
    if (!problems.isEmpty()) {
      throw new GradleException(String.join(System.lineSeparator(), problems));
    }
  }

  private int run(Path root, List<String> command, boolean withoutSession) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exit =
        getExecOperations()
            .exec(
                spec -> {
                  spec.setWorkingDir(root.toFile());
                  spec.commandLine(command);
                  spec.setIgnoreExitValue(true);
                  spec.setStandardOutput(output);
                  spec.setErrorOutput(output);
                  if (withoutSession) {
                    Map<String, Object> environment = new HashMap<>(spec.getEnvironment());
                    environment.remove("CLAUDE_PID");
                    spec.setEnvironment(environment);
                  }
                })
            .getExitValue();
    if (exit != 0) {
      getLogger().error(output.toString(StandardCharsets.UTF_8));
    }
    return exit;
  }
}
