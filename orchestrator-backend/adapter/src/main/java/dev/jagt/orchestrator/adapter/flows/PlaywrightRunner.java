package dev.jagt.orchestrator.adapter.flows;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.port.AcceptanceRunner;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.OneLine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

@Component
@ConditionalOnProperty(
    prefix = "orchestrator.roles.acceptance.flows",
    name = "driver",
    havingValue = "playwright")
@RequiredArgsConstructor
@Slf4j
public class PlaywrightRunner implements AcceptanceRunner {

  private final Processes processes;
  private final FlowsProperties properties;

  @Override
  public List<FlowResult> run(Path worktree, List<String> flows, String environment) {
    if (flows.isEmpty()) {
      return List.of();
    }
    if (properties.path().isEmpty() || properties.command().isEmpty()) {
      return Unrun.all(flows, "the flows path or command is not configured");
    }
    Path evidence = worktree.resolve("acceptance");
    Processes.Result ran;
    try {
      Files.createDirectories(evidence);
      ran =
          processes.run(
              Path.of(properties.path()),
              properties.timeout(),
              Map.of("BASE_URL", environment),
              argv(flows, evidence));
    } catch (IOException | IllegalStateException e) {
      return unrun(flows, "the flows command did not run", e.toString());
    }
    return read(flows, ran, evidence.resolve("report.json"));
  }

  private List<FlowResult> read(List<String> flows, Processes.Result ran, Path report) {
    try {
      PlaywrightReport parsed = new PlaywrightReport(ran.stdout());
      if (parsed.isReport()) {
        Files.writeString(report, ran.stdout());
        return parsed.results(flows, report);
      }
    } catch (JacksonException | IOException e) {
      log.atDebug()
          .setMessage("scripted flows report unreadable")
          .addKeyValue("cause", e.toString())
          .log();
    }
    return unrun(
        flows,
        "no JSON report (exit " + ran.exitCode() + ")",
        Objects.toString(OneLine.clipped(ran.stderr()), ""));
  }

  private List<String> argv(List<String> flows, Path evidence) {
    List<String> argv = new ArrayList<>(properties.command());
    argv.set(0, Executables.resolve(argv.get(0)));
    argv.add("--reporter=json");
    argv.add("--output=" + evidence.resolve("artifacts"));
    argv.add("--grep");
    argv.add(String.join("|", flows.stream().map(PlaywrightRunner::escaped).toList()));
    return argv;
  }

  private static String escaped(String flow) {
    return flow.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0");
  }

  private static List<FlowResult> unrun(List<String> flows, String reason, String cause) {
    log.atWarn()
        .setMessage("scripted flows unprovable")
        .addKeyValue("reason", reason)
        .addKeyValue("cause", cause)
        .log();
    return Unrun.all(flows, reason);
  }
}
