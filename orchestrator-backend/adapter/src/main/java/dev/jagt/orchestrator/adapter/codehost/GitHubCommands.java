package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.CodeHostCommands;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GitHubCommands implements CodeHostCommands {

  private static final Map<String, Set<String>> READS =
      Map.of(
          "pr", Set.of("view", "list", "diff", "checks", "status"),
          "issue", Set.of("view", "list", "status"),
          "run", Set.of("view", "list"),
          "repo", Set.of("view"),
          "auth", Set.of("status"));
  private static final Set<String> GROUPS =
      Set.of(
          "agent-task",
          "alias",
          "api",
          "attestation",
          "auth",
          "browse",
          "cache",
          "co",
          "codespace",
          "completion",
          "config",
          "copilot",
          "cs",
          "extension",
          "extensions",
          "ext",
          "gist",
          "gpg-key",
          "issue",
          "label",
          "licenses",
          "org",
          "pr",
          "preview",
          "project",
          "release",
          "repo",
          "ruleset",
          "run",
          "search",
          "secret",
          "skill",
          "skills",
          "ssh-key",
          "status",
          "variable",
          "var",
          "workflow");

  @Override
  public String command() {
    return "gh";
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
