package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict.Status;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class AcceptanceCases {

  private AcceptanceCases() {}

  public static List<AcceptanceCase> all() {
    return RoleCases.of("acceptance", AcceptanceCases::parse);
  }

  public static AcceptanceCase parse(String yaml) {
    Map<String, Object> root = Yamls.load(yaml);
    Map<String, Object> ticket = Yamls.map(root.get("ticket"));
    Map<String, Object> deploy = Yamls.map(root.get("deploy"));
    Map<String, Object> expect = Yamls.map(root.get("expect"));
    List<AcceptanceCase.Criterion> criteria =
        Yamls.list(ticket.get("criteria")).stream()
            .map(Yamls::map)
            .map(criterion ->
                new AcceptanceCase.Criterion(
                    Yamls.text(criterion.get("id")),
                    Yamls.text(criterion.get("text")),
                    !Boolean.FALSE.equals(criterion.get("testable"))))
            .toList();
    Map<String, Status> rows = new LinkedHashMap<>();
    Yamls.map(expect.get("rows"))
        .forEach((id, status) -> rows.put(id, Status.of(Yamls.text(status)).orElseThrow()));
    return new AcceptanceCase(
        Yamls.text(root.get("case")),
        new AcceptanceCase.Ticket(
            Yamls.text(ticket.get("key")), Yamls.text(ticket.get("title")), criteria),
        new AcceptanceCase.Landing(
            new AcceptanceCase.Deploy(
                Yamls.text(deploy.get("sha")),
                Yamls.text(deploy.get("landed")),
                Yamls.texts(deploy.get("diff")),
                deploy.get("landed_sha_served") == null
                    ? ""
                    : Yamls.text(deploy.get("landed_sha_served"))),
            Yamls.mapOrEmpty(root.get("environment"))),
        decline(root),
        new AcceptanceCase.Expect(
            rows,
            Yamls.text(expect.get("report")),
            expect.get("probe") == null ? "" : Yamls.text(expect.get("probe")),
            Yamls.text(expect.get("ending")),
            Yamls.texts(root.get("names"))),
        flows(root),
        workspace(root));
  }

  private static AcceptanceCase.Workspace workspace(Map<String, Object> root) {
    Map<String, Object> expect = Yamls.map(root.get("expect"));
    return new AcceptanceCase.Workspace(
        Yamls.textsByKey(Yamls.mapOrEmpty(root.get("workspace"))),
        expect.get("writes") == null ? "" : Yamls.text(expect.get("writes")));
  }

  private static AcceptanceCase.Flows flows(Map<String, Object> root) {
    Map<String, Object> files = Yamls.mapOrEmpty(root.get("flows"));
    Map<String, Object> flows = Yamls.mapOrEmpty(Yamls.map(root.get("expect")).get("flows"));
    Map<String, String> content = Yamls.textsByKey(files);
    content.remove("path");
    content.remove("command");
    return new AcceptanceCase.Flows(
        content,
        Yamls.listOrEmpty(flows.get("ids")).stream().map(Yamls::text).toList(),
        files.containsKey("path") ? Yamls.text(files.get("path")) : "",
        Yamls.listOrEmpty(files.get("command")).stream().map(Yamls::text).toList());
  }

  private static AcceptanceCase.@Nullable Decline decline(Map<String, Object> root) {
    if (root.get("session_reply") == null) {
      return null;
    }
    Map<String, Object> reply = Yamls.map(root.get("session_reply"));
    Map<String, Object> previous = Yamls.map(root.get("previous_check"));
    return new AcceptanceCase.Decline(
        Yamls.text(previous.get("evidence")), Yamls.text(reply.get("why")));
  }
}
