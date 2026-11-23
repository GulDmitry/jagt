package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestActStopsBuilder.actStops;
import static dev.jagt.orchestrator.service.TestConfigSourceBuilder.configSource;
import static dev.jagt.orchestrator.service.TestKeptNumbersBuilder.keptNumbers;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.With;

@With
public record TestConfigServiceBuilder(Path root, String yaml) {

  public static TestConfigServiceBuilder configService() {
    return new TestConfigServiceBuilder(
        Path.of("/state"),
        "orchestrator:\n  projects: {}\n  agent:\n    submitWait: 0\n    draftPoll: 0\n");
  }

  public ConfigService build(AgentRuntime runtime) throws IOException {
    Files.writeString(root.resolve("jagt.yml"), yaml);
    return new ConfigService(
        configSource().withRoot(root).build(),
        runtime,
        actStops().withRoot(root).build(),
        new Checkers(new TaskChart(Composition.builtIn())),
        keptNumbers().build());
  }
}
