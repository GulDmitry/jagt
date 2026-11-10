package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.task.StopTriage;
import dev.jagt.orchestrator.task.TaskStop;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class StopTriageCases {

  private StopTriageCases() {}

  public static List<StopTriageCase> all() {
    return RoleCases.of("triage", StopTriageCases::parse);
  }

  public static StopTriageCase parse(String yaml) {
    Map<String, Object> root = Yamls.load(yaml);
    Map<String, Object> stop = Yamls.map(root.get("stop"));
    Map<String, Object> expect = Yamls.map(root.get("expect"));
    return new StopTriageCase(
        Yamls.text(root.get("case")),
        TaskStop.Trigger.valueOf(Yamls.text(stop.get("trigger"))),
        Yamls.text(stop.get("evidence")),
        Yamls.text(root.get("trail")),
        Yamls.text(root.get("commits")),
        StopTriage.Who.valueOf(constant(expect.get("who"))),
        StopTriage.Lift.valueOf(constant(expect.get("lift"))),
        String.valueOf(expect.get("family")));
  }

  private static String constant(@Nullable Object value) {
    return Yamls.text(value).toUpperCase(Locale.ROOT);
  }
}
