package dev.jagt.orchestrator;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.yaml.snakeyaml.Yaml;

final class CiWorkflow {

  private static final Path WORKFLOW = Path.of("../.github/workflows/ci.yml");

  private CiWorkflow() {}

  static Map<String, Map<String, Object>> jobs() {
    Map<?, ?> workflow = new Yaml().load(SourceRoots.read(WORKFLOW));
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
