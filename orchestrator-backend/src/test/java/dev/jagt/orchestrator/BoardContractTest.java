package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import dev.jagt.orchestrator.flow.deploy.Pipeline;
import dev.jagt.orchestrator.flow.view.Attention;
import dev.jagt.orchestrator.flow.view.DashboardLine;
import dev.jagt.orchestrator.flow.view.Owner;
import dev.jagt.orchestrator.flow.view.Phase;
import dev.jagt.orchestrator.flow.view.TaskView;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.AutoReviewWatch;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

class BoardContractTest {

  private static final Path OPENAPI = Path.of("../contracts/openapi.yaml");
  private static final Path EVENTS = Path.of("../contracts/board-events.schema.json");

  @Test
  void taskContractAcceptsEveryTypedFieldOfSerialisedTaskView() throws IOException {
    JsonNode contract = new YAMLMapper().readTree(Files.readString(OPENAPI));
    ObjectNode root = new JsonMapper().createObjectNode();
    root.set("components", contract.path("components"));
    root.put("$ref", "#/components/schemas/Task");
    Schema task =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(root.toString());
    TaskView view =
        new TaskView(
            new TaskView.Identity(
                "t-1", "alias", "demo", "A title", "https://example.test/ABC-42", "high"),
            new TaskView.Standing(
                TaskStatus.IN_PROGRESS,
                "agent working",
                Phase.BUILD,
                Owner.YOU,
                Attention.REQUIRED,
                1L,
                List.of(
                    new TaskView.Step(
                        TaskStatus.NEW, "starting", 2L, ActionOrigin.BOARD, "why", "reviewer")),
                new TaskView.Stop(
                    TaskStop.Trigger.Kind.GUARD, "reason", "evidence", "next", 3L, "triage")),
            new TaskView.Ask(
                "ask",
                "hint",
                List.of(
                    new TaskView.ActionView(
                        "ship",
                        "Ship",
                        "hint",
                        "s",
                        new TaskView.ActionView.Emphasis(true, false, true),
                        new TaskView.ActionView.Placement("main", true, false))),
                Map.of("ship", "Sure?"),
                "detail",
                DashboardLine.Kind.NOTE,
                "waiting"),
            new TaskView.Request(
                "https://example.test/pr/1", 4L, true, Pipeline.GREEN, "success", false),
            new TaskView.Replies(false, 5L, AutoReviewWatch.watching(6L), Map.of("a", "yes")),
            new TaskView.Delivery(
                List.of(new TaskView.RepoView("demo", "https://example.test/pr/1")), true));

    String json = new JsonMapper().writeValueAsString(view);

    assertThat(task.validate(json, InputFormat.JSON)).isEmpty();
  }

  @Test
  void eventContractAcceptsTheRoleLogFrame() throws IOException {
    Schema events =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(Files.readString(EVENTS));

    assertThat(events.validate("{\"event\":\"role-log\",\"data\":\"reviewer\"}", InputFormat.JSON))
        .isEmpty();
  }
}
