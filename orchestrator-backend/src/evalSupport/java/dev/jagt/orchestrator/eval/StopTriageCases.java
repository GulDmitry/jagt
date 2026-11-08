package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.task.StopTriage;
import dev.jagt.orchestrator.task.TaskStop;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class StopTriageCases {

  private StopTriageCases() {}

  public static List<StopTriageCase> all() {
    return RoleCases.of("triage", StopTriageCases::parse);
  }

  @SuppressWarnings("unchecked")
  public static StopTriageCase parse(String yaml) {
    Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
    Map<String, Object> stop = (Map<String, Object>) root.get("stop");
    Map<String, Object> expect = (Map<String, Object>) root.get("expect");
    return new StopTriageCase(
        (String) root.get("case"),
        TaskStop.Trigger.valueOf((String) stop.get("trigger")),
        (String) stop.get("evidence"),
        (String) root.get("trail"),
        (String) root.get("commits"),
        StopTriage.Who.valueOf(constant(expect.get("who"))),
        StopTriage.Lift.valueOf(constant(expect.get("lift"))),
        String.valueOf(expect.get("family")));
  }

  private static String constant(Object value) {
    return String.valueOf(value).toUpperCase(Locale.ROOT);
  }
}
