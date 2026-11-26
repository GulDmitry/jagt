package dev.jagt.orchestrator;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.Yaml;

public final class CiWorkflow {

  private static final Path WORKFLOW = Path.of("../.github/workflows/ci.yml");

  private static final Path NIGHTLY = Path.of("../.github/workflows/nightly.yml");

  private static final Path GITLAB = Path.of("../.gitlab-ci.yml");

  private CiWorkflow() {}

  public static Set<String> githubTriggers() {
    Map<?, ?> workflow = new Yaml().load(SourceRoots.read(WORKFLOW));
    Map<?, ?> on = (Map<?, ?>) Objects.requireNonNull(workflow.get(true));
    return on.keySet().stream().map(String::valueOf).collect(Collectors.toSet());
  }

  public static Set<String> gitlabJobs() {
    Map<?, ?> pipeline = new Yaml().load(SourceRoots.read(GITLAB));
    return pipeline.entrySet().stream()
        .filter(
            entry -> entry.getValue() instanceof Map<?, ?> fields && fields.containsKey("stage"))
        .map(entry -> (String) entry.getKey())
        .collect(Collectors.toSet());
  }

  static String gitlabText() {
    return SourceRoots.read(GITLAB);
  }

  public static Map<String, Map<String, Object>> jobs() {
    return jobsOf(WORKFLOW);
  }

  static Map<String, Map<String, Object>> nightlyJobs() {
    return jobsOf(NIGHTLY);
  }

  static Map<String, String> gateLinesByJob(Map<String, Map<String, Object>> jobs) {
    Map<String, String> byLine = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, Object>> job : jobs.entrySet()) {
      for (Object step : (List<?>) job.getValue().getOrDefault("steps", List.of())) {
        String run = String.valueOf(((Map<?, ?>) step).get("run"));
        run.lines()
            .map(String::strip)
            .filter(
                line -> line.startsWith("scripts/linux-suite.sh ") || line.startsWith("./gradlew "))
            .forEach(
                line -> byLine.merge(line, job.getKey(), (first, second) -> first + ", " + second));
      }
    }
    return byLine;
  }

  private static Map<String, Map<String, Object>> jobsOf(Path file) {
    Map<?, ?> workflow = new Yaml().load(SourceRoots.read(file));
    Map<String, Map<String, Object>> jobs = new LinkedHashMap<>();
    Map<?, ?> declared = (Map<?, ?>) Objects.requireNonNull(workflow.get("jobs"));
    for (Map.Entry<?, ?> job : declared.entrySet()) {
      Map<String, Object> fields = new LinkedHashMap<>();
      for (Map.Entry<?, ?> field : ((Map<?, ?>) job.getValue()).entrySet()) {
        fields.put((String) field.getKey(), field.getValue());
      }
      jobs.put((String) job.getKey(), fields);
    }
    return jobs;
  }
}
