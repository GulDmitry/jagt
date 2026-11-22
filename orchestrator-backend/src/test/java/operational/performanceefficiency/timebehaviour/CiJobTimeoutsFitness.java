package operational.performanceefficiency.timebehaviour;

import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;

public final class CiJobTimeoutsFitness implements Fitness {

  private static final String GITLAB_RESERVED = "default";

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    Path repository = Objects.requireNonNull(project.root().getParent());
    List<String> unlimited = new ArrayList<>();
    try (Stream<Path> workflows = Files.list(repository.resolve(".github/workflows"))) {
      for (Path workflow :
          workflows.filter(path -> path.toString().endsWith(".yml")).sorted().toList()) {
        githubJobs(workflow)
            .forEach(job -> unlimited.add("github " + workflow.getFileName() + " " + job));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    gitlabJobs(repository.resolve(".gitlab-ci.yml")).forEach(job -> unlimited.add("gitlab " + job));
    return Measurement.of(unlimited);
  }

  private static List<String> githubJobs(Path workflow) {
    Map<?, ?> jobs = (Map<?, ?>) Objects.requireNonNull(load(workflow).get("jobs"));
    return jobs.entrySet().stream()
        .filter(job -> job.getValue() instanceof Map<?, ?>)
        .filter(job -> !((Map<?, ?>) job.getValue()).containsKey("uses"))
        .filter(job -> !((Map<?, ?>) job.getValue()).containsKey("timeout-minutes"))
        .map(job -> String.valueOf(job.getKey()))
        .toList();
  }

  private static List<String> gitlabJobs(Path pipeline) {
    Map<String, Object> declared = load(pipeline);
    boolean defaulted =
        declared.get(GITLAB_RESERVED) instanceof Map<?, ?> defaults
            && defaults.containsKey("timeout");
    return declared.entrySet().stream()
        .filter(
            entry -> entry.getValue() instanceof Map<?, ?> fields && fields.containsKey("stage"))
        .filter(entry -> !defaulted && !((Map<?, ?>) entry.getValue()).containsKey("timeout"))
        .map(Map.Entry::getKey)
        .toList();
  }

  private static Map<String, Object> load(Path file) {
    try {
      return new Yaml().load(Files.readString(file));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
