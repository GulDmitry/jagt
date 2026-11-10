package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class MasterCases {

  private MasterCases() {}

  public static List<MasterCase> all() {
    return RoleCases.of("master", MasterCases::parse);
  }

  public static MasterCase parse(String yaml) {
    Map<String, Object> root = Yamls.load(yaml);
    Map<String, Object> expect = Yamls.map(root.get("expect"));
    return new MasterCase(
        Yamls.text(root.get("case")),
        Yamls.text(root.get("instructions")),
        TaskStatus.valueOf(constant(root.get("status"))),
        Yamls.textsByKey(root.get("baseline")),
        Yamls.textsByKey(Yamls.mapOrEmpty(root.get("change"))),
        root.get("plan") == null ? "" : Yamls.text(root.get("plan")),
        new MasterCase.Expect(
            expect.get("verdict") == null
                ? null
                : MasterReview.Kind.valueOf(constant(expect.get("verdict"))),
            Yamls.texts(expect.get("names"))),
        root.get("question") == null
            ? MasterCase.Answer.NONE
            : answer(Yamls.map(root.get("question")), expect));
  }

  private static MasterCase.Answer answer(
      Map<String, Object> question, Map<String, Object> expect) {
    return new MasterCase.Answer(
        Yamls.text(question.get("asked")),
        Yamls.texts(question.get("human_said")),
        Yamls.texts(expect.get("refused")));
  }

  private static String constant(@Nullable Object value) {
    return Yamls.text(value).toUpperCase(Locale.ROOT).replace('-', '_');
  }
}
