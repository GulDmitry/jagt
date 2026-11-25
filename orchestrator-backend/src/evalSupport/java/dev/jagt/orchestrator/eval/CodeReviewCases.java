package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.eval.CodeReviewCase.Expected;
import dev.jagt.orchestrator.eval.CodeReviewCase.Finding;
import dev.jagt.orchestrator.eval.CodeReviewCase.Note;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class CodeReviewCases {

  private CodeReviewCases() {}

  public static List<CodeReviewCase> all() {
    return RoleCases.of("code-reviewer", CodeReviewCases::parse);
  }

  public static CodeReviewCase parse(String yaml) {
    Map<String, Object> root = Yamls.load(yaml);
    Map<String, Object> expect = Yamls.map(root.get("expect"));
    return new CodeReviewCase(
        Yamls.text(root.get("case")),
        Yamls.text(root.get("description")),
        Yamls.text(root.get("diff")),
        optional(root.get("botNote")),
        new Expected(
            Yamls.text(expect.get("verdict")).equals("ready"),
            Yamls.listOrEmpty(expect.get("findings")).stream()
                .map(Yamls::map)
                .map(CodeReviewCases::finding)
                .toList(),
            note(expect.get("note"))));
  }

  private static Note note(@Nullable Object row) {
    if (row == null) {
      return Note.UNHELD;
    }
    Map<String, Object> limits = Yamls.map(row);
    return new Note(
        Integer.parseInt(Yamls.text(limits.get("maxLines"))),
        Integer.parseInt(Yamls.text(limits.get("maxWords"))));
  }

  private static Finding finding(Map<String, Object> row) {
    return new Finding(
        optional(row.get("file")),
        Pattern.compile(Yamls.text(row.get("pattern")), Pattern.CASE_INSENSITIVE),
        row.get("severity") == null ? Set.of() : Set.copyOf(Yamls.texts(row.get("severity"))));
  }

  private static String optional(@Nullable Object value) {
    return value == null ? "" : Yamls.text(value);
  }
}
