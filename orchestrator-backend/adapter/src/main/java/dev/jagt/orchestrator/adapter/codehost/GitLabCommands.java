package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.CodeHostCommands;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GitLabCommands implements CodeHostCommands {

  private static final Map<String, Set<String>> READS =
      Map.of(
          "mr", Set.of("view", "list", "diff"),
          "issue", Set.of("view", "list"),
          "ci", Set.of("list", "status"),
          "repo", Set.of("view"),
          "auth", Set.of("status"));
  private static final Set<String> GROUPS =
      Set.of(
          "alias",
          "api",
          "artifact-registry",
          "auth",
          "at",
          "changelog",
          "check-update",
          "ci",
          "cluster",
          "completion",
          "config",
          "container-registry",
          "dependency-firewall",
          "deploy-key",
          "discussion",
          "duo",
          "govern",
          "incident",
          "issue",
          "iteration",
          "job",
          "label",
          "mcp",
          "milestone",
          "mr",
          "opentofu",
          "orbit",
          "packages",
          "pipe",
          "pipeline",
          "project",
          "release",
          "repo",
          "runner",
          "runner-controller",
          "schedule",
          "securefile",
          "security",
          "snippet",
          "stack",
          "todo",
          "token",
          "user",
          "variable",
          "work-items");

  @Override
  public String command() {
    return "glab";
  }

  @Override
  public Map<String, Set<String>> reads() {
    return READS;
  }

  @Override
  public Set<String> groups() {
    return GROUPS;
  }
}
