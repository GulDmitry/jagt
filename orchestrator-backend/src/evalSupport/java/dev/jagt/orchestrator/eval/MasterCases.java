package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class MasterCases {

  private MasterCases() {}

  public static List<MasterCase> all() {
    return RoleCases.of("master", MasterCases::parse);
  }

  @SuppressWarnings("unchecked")
  public static MasterCase parse(String yaml) {
    Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
    Map<String, Object> expect = (Map<String, Object>) root.get("expect");
    Map<String, Object> question = (Map<String, Object>) root.get("question");
    return new MasterCase(
        (String) root.get("case"),
        (String) root.get("instructions"),
        TaskStatus.valueOf(constant(root.get("status"))),
        (Map<String, String>) root.get("baseline"),
        (Map<String, String>) root.getOrDefault("change", Map.of()),
        (String) root.getOrDefault("plan", ""),
        expect.get("verdict") == null
            ? null
            : MasterReview.Kind.valueOf(constant(expect.get("verdict"))),
        (List<String>) expect.get("names"),
        question == null
            ? MasterCase.Answer.NONE
            : new MasterCase.Answer(
                (String) question.get("asked"),
                (List<String>) question.get("human_said"),
                (List<String>) expect.get("refused")));
  }

  private static String constant(Object value) {
    return String.valueOf(value).toUpperCase(Locale.ROOT).replace('-', '_');
  }
}
