package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.task.AcceptanceVerdict.Status;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class AcceptanceCases {

  private AcceptanceCases() {}

  public static List<AcceptanceCase> all() {
    return RoleCases.of("acceptance", AcceptanceCases::parse);
  }

  @SuppressWarnings("unchecked")
  public static AcceptanceCase parse(String yaml) {
    Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
    Map<String, Object> ticket = (Map<String, Object>) root.get("ticket");
    Map<String, Object> deploy = (Map<String, Object>) root.get("deploy");
    Map<String, Object> expect = (Map<String, Object>) root.get("expect");
    List<AcceptanceCase.Criterion> criteria =
        ((List<Map<String, Object>>) ticket.get("criteria"))
            .stream()
                .map(
                    criterion ->
                        new AcceptanceCase.Criterion(
                            text(criterion.get("id")),
                            text(criterion.get("text")),
                            !Boolean.FALSE.equals(criterion.get("testable"))))
                .toList();
    Map<String, Status> rows = new LinkedHashMap<>();
    ((Map<String, Object>) expect.get("rows"))
        .forEach((id, status) -> rows.put(id, Status.of(text(status)).orElseThrow()));
    return new AcceptanceCase(
        text(root.get("case")),
        text(ticket.get("key")),
        text(ticket.get("title")),
        criteria,
        new AcceptanceCase.Deploy(
            text(deploy.get("sha")),
            text(deploy.get("landed")),
            ((List<Object>) deploy.get("diff")).stream().map(AcceptanceCases::text).toList(),
            deploy.get("landed_sha_served") == null ? "" : text(deploy.get("landed_sha_served"))),
        (Map<String, Object>) root.getOrDefault("environment", Map.of()),
        decline(root),
        rows,
        text(expect.get("report")),
        expect.get("probe") == null ? "" : text(expect.get("probe")),
        text(expect.get("ending")),
        ((List<Object>) root.get("names")).stream().map(AcceptanceCases::text).toList(),
        flows(root),
        workspace(root));
  }

  @SuppressWarnings("unchecked")
  private static AcceptanceCase.Workspace workspace(Map<String, Object> root) {
    Map<String, Object> files = (Map<String, Object>) root.getOrDefault("workspace", Map.of());
    Map<String, Object> expect = (Map<String, Object>) root.get("expect");
    Map<String, String> content = new LinkedHashMap<>();
    files.forEach((name, text) -> content.put(name, text(text)));
    return new AcceptanceCase.Workspace(
        content, expect.get("writes") == null ? "" : text(expect.get("writes")));
  }

  @SuppressWarnings("unchecked")
  private static AcceptanceCase.Flows flows(Map<String, Object> root) {
    Map<String, Object> files = (Map<String, Object>) root.getOrDefault("flows", Map.of());
    Map<String, Object> expect = (Map<String, Object>) root.get("expect");
    Map<String, Object> flows = (Map<String, Object>) expect.getOrDefault("flows", Map.of());
    Map<String, String> content = new LinkedHashMap<>();
    files.forEach((name, text) -> content.put(name, text(text)));
    return new AcceptanceCase.Flows(
        content,
        ((List<Object>) flows.getOrDefault("ids", List.of()))
            .stream().map(AcceptanceCases::text).toList());
  }

  @SuppressWarnings("unchecked")
  private static AcceptanceCase.Decline decline(Map<String, Object> root) {
    Map<String, Object> reply = (Map<String, Object>) root.get("session_reply");
    if (reply == null) {
      return null;
    }
    Map<String, Object> previous = (Map<String, Object>) root.get("previous_check");
    return new AcceptanceCase.Decline(text(previous.get("evidence")), text(reply.get("why")));
  }

  private static String text(Object value) {
    return String.valueOf(value);
  }
}
