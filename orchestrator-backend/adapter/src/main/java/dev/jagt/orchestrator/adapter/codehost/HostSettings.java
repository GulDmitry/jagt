package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.task.ProjectConfig.CodeHost;
import java.nio.file.Path;

/**
 * What a project's {@code jagt.yml} gives the driver answering for it; {@code selectedBy} is the
 * key that chose the driver.
 */
public record HostSettings(String project, String selectedBy, Path dir, CodeHost host) {

  private static final String ROOT = "orchestrator";

  public String key(String name) {
    return ROOT + ".projects." + project + "." + name;
  }
}
