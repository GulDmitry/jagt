package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StandToolsTest {

  @TempDir Path directory;

  @Test
  void getTicketAnswersTheSeededTicket() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedTicket("ABC-42", "Title", "Body", "open");

    JsonNode ticket =
        new StandTools(store)
            .call("get_ticket", JsonMapper.builder().build().readTree("{\"key\":\"ABC-42\"}"));

    assertThat(ticket.path("title").asString()).isEqualTo("Title");
  }

  @Test
  void listTicketsAnswersEverySeededTicket() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.seedTicket("ABC-42", "Title", "Body", "open");

    JsonNode tickets =
        new StandTools(store).call("list_tickets", JsonMapper.builder().build().readTree("{}"));

    assertThat(tickets).hasSize(1);
  }

  @Test
  void createMergeRequestOpensOneWithRunningPipeline() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");

    JsonNode created =
        new StandTools(store)
            .call(
                "create_merge_request",
                JsonMapper.builder()
                    .build()
                    .readTree(
                        "{\"source_branch\":\"f\",\"target_branch\":\"main\","
                            + "\"title\":\"t\",\"description\":\"d\"}"));

    assertThat(created.path("pipeline").asString()).isEqualTo("running");
  }

  @Test
  void updateMergeRequestChangesTitleAndDescription() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.createRequest("f", "main", "t", "d");

    new StandTools(store)
        .call(
            "update_merge_request",
            JsonMapper.builder()
                .build()
                .readTree("{\"id\":1,\"title\":\"n\",\"description\":\"m\"}"));

    assertThat(store.request(1).path("title").asString()).isEqualTo("n");
  }

  @Test
  void getMergeRequestReadsByTheTrailingIdOfAnUrl() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.createRequest("f", "main", "t", "d");

    JsonNode request =
        new StandTools(store)
            .call(
                "get_merge_request",
                JsonMapper.builder()
                    .build()
                    .readTree("{\"id_or_url\":\"http://127.0.0.1:1/requests/1\"}"));

    assertThat(request.path("id").asInt()).isEqualTo(1);
  }

  @Test
  void mergeRequestReferenceThatIsNoIdIsFailure() {
    StandTools tools = new StandTools(new StandStore(directory, "http://127.0.0.1:1"));

    assertThatThrownBy(() ->
            tools.call(
                "get_merge_request",
                JsonMapper.builder().build().readTree("{\"id_or_url\":\"abc\"}")))
        .isInstanceOf(StandFailure.class);
  }

  @Test
  void pipelinesOfMergeRequestReportItsPipelineStatus() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.createRequest("f", "main", "t", "d");

    JsonNode pipelines =
        new StandTools(store)
            .call(
                "list_merge_request_pipelines",
                JsonMapper.builder().build().readTree("{\"id\":1}"));

    assertThat(pipelines.get(0).path("status").asString()).isEqualTo("running");
    assertThat(pipelines.get(0).path("ref").asString()).isEqualTo("f");
  }

  @Test
  void listMergeRequestsFiltersBySourceBranchWhenGiven() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.createRequest("f", "main", "t", "d");
    store.createRequest("g", "main", "t", "d");

    JsonNode one =
        new StandTools(store)
            .call(
                "list_merge_requests",
                JsonMapper.builder().build().readTree("{\"source_branch\":\"g\"}"));
    JsonNode all =
        new StandTools(store)
            .call("list_merge_requests", JsonMapper.builder().build().readTree("{}"));

    assertThat(one).hasSize(1);
    assertThat(all).hasSize(2);
  }

  @Test
  void replyAndResolveActAsTheAgentOnThread() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.createRequest("f", "main", "t", "d");
    store.addThread(1, "greet.sh", 2, "stiff");
    StandTools tools = new StandTools(store);

    tools.call(
        "reply_to_thread",
        JsonMapper.builder().build().readTree("{\"id\":1,\"thread_id\":1,\"body\":\"kept\"}"));
    tools.call(
        "resolve_thread", JsonMapper.builder().build().readTree("{\"id\":1,\"thread_id\":1}"));

    JsonNode thread = store.request(1).path("threads").get(0);
    assertThat(thread.path("replies").get(0).path("author").asString()).isEqualTo("agent");
    assertThat(thread.path("resolved").asBoolean()).isTrue();
  }

  @Test
  void theCurrentUserIsTheAgent() {
    StandTools tools = new StandTools(new StandStore(directory, "http://127.0.0.1:1"));

    assertThat(
            tools
                .call("get_current_user", JsonMapper.builder().build().readTree("{}"))
                .path("username")
                .asString())
        .isEqualTo("agent");
  }

  @Test
  void anUnknownToolIsFailure() {
    StandTools tools = new StandTools(new StandStore(directory, "http://127.0.0.1:1"));

    assertThatThrownBy(() -> tools.call("nope", JsonMapper.builder().build().readTree("{}")))
        .isInstanceOf(StandFailure.class);
  }

  @Test
  void missingStringArgumentIsFailure() {
    StandTools tools = new StandTools(new StandStore(directory, "http://127.0.0.1:1"));

    assertThatThrownBy(() -> tools.call("get_ticket", JsonMapper.builder().build().readTree("{}")))
        .isInstanceOf(StandFailure.class);
  }

  @Test
  void missingIntegerArgumentIsFailure() {
    StandTools tools = new StandTools(new StandStore(directory, "http://127.0.0.1:1"));

    assertThatThrownBy(() ->
            tools.call("list_merge_request_pipelines", JsonMapper.builder().build().readTree("{}")))
        .isInstanceOf(StandFailure.class);
  }
}
