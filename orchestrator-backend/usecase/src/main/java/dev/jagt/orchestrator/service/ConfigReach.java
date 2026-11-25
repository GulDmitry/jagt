package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.service.reach.SessionReach;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ConfigReach implements SessionReach {

  private final ConfigService config;
  private final Host host;

  @Override
  public List<Path> beside() {
    return config.load().agent().sandboxOrDefault().allowWritePaths(host.home()).stream()
        .filter(Files::isDirectory)
        .map(WorktreeSandbox::real)
        .toList();
  }
}
