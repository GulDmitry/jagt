package dev.jagt.orchestrator.stand;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public class FakeGh {

  private static final Pattern PULL = Pattern.compile("/pull/(\\d+)");
  private static final Pattern POSTED =
      Pattern.compile("repos/.+/(?:issues|pulls)/(\\d+)/comments(?:/(\\d+)/replies)?");
  private static final Pattern REACTED = Pattern.compile("comments/(\\d+)/reactions");
  private static final String RUNS = "https://stand.example/actions/runs/1";

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final StandStore store;
  private final AtomicReference<Runnable> afterPost = new AtomicReference<>(() -> {});

  public FakeGh(StandStore store) {
    this.store = store;
  }

  public void afterPost(Runnable hook) {
    afterPost.set(hook);
  }

  public String run(List<String> args) {
    String group = args.isEmpty() ? "" : args.getFirst();
    return switch (group) {
      case "pr" -> pr(args);
      case "run" -> runs(args);
      case "api" -> api(args);
      case "auth" -> "Logged in to stand.example";
      default -> throw refuse(args);
    };
  }

  private String pr(List<String> args) {
    String verb = args.size() > 1 ? args.get(1) : "";
    return switch (verb) {
      case "list" -> {
        if (!args.contains("--search")) {
          throw refuse(args);
        }
        yield awaiting(value(args, "-R"));
      }
      case "view" -> view(args);
      case "diff" -> store.pull(pullOf(args.get(2))).path("diff").asString();
      case "review", "merge" -> throw approval(args);
      default -> throw refuse(args);
    };
  }

  private String awaiting(String repository) {
    ArrayNode list = mapper.createArrayNode();
    for (JsonNode pull : store.awaitingPulls()) {
      ObjectNode one = list.addObject();
      one.put("url", "https://" + repository + "/pull/" + pull.path("number").asInt());
      one.put("title", pull.path("title").asString());
      one.putObject("author").put("login", pull.path("author").asString());
      one.put("headRefOid", pull.path("head").asString());
    }
    return list.toString();
  }

  private String view(List<String> args) {
    if (args.contains("--jq")) {
      return "stand";
    }
    JsonNode pull = store.pull(pullOf(args.get(2)));
    return mapper
        .createObjectNode()
        .put("title", pull.path("title").asString())
        .put("body", pull.path("description").asString())
        .put("baseRefName", "main")
        .put("headRefOid", pull.path("head").asString())
        .put("state", pull.path("state").asString().toUpperCase(Locale.ROOT))
        .toString();
  }

  private String runs(List<String> args) {
    if (args.size() < 2 || !args.get(1).equals("list")) {
      throw refuse(args);
    }
    if (!args.contains("--commit")) {
      return "[]";
    }
    ArrayNode list = mapper.createArrayNode();
    list.addObject().put("status", "completed").put("conclusion", "success").put("url", RUNS);
    return list.toString();
  }

  private String api(List<String> args) {
    if (args.contains("graphql")) {
      return graphql(Integer.parseInt(fields(args).getOrDefault("number", "0")));
    }
    String endpoint = args.stream().filter(arg -> arg.startsWith("repos/")).findFirst().orElse("");
    String method = args.contains("-X") ? value(args, "-X") : "GET";
    if (method.equals("POST") && POSTED.matcher(endpoint).matches()) {
      return post(endpoint, fields(args));
    }
    if (!method.equals("GET") || endpoint.endsWith("/reviews") || endpoint.endsWith("/merge")) {
      throw approval(args);
    }
    Matcher reacted = REACTED.matcher(endpoint);
    if (!reacted.find()) {
      throw refuse(args);
    }
    JsonNode note = store.note(Integer.parseInt(reacted.group(1)));
    ArrayNode reactions = mapper.createArrayNode();
    for (int i = 0; i < note.path("likes").asInt(); i++) {
      reactions.addObject().put("content", "+1");
    }
    for (int i = 0; i < note.path("dislikes").asInt(); i++) {
      reactions.addObject().put("content", "-1");
    }
    return reactions.toString();
  }

  private String post(String endpoint, Map<String, String> fields) {
    Matcher posted = POSTED.matcher(endpoint);
    posted.matches();
    int parent = posted.group(2) == null ? 0 : Integer.parseInt(posted.group(2));
    ObjectNode note =
        store.addNote(
            Integer.parseInt(posted.group(1)),
            StandStore.ACTOR,
            "actor",
            fields.getOrDefault("body", ""),
            fields.getOrDefault("path", ""),
            parent);
    afterPost.get().run();
    return mapper.createObjectNode().put("id", note.path("id").asInt()).toString();
  }

  private String graphql(int number) {
    ObjectNode request = mapper.createObjectNode();
    ObjectNode data = request.putObject("data");
    data.putObject("viewer").put("login", StandStore.ACTOR);
    ObjectNode pull = data.putObject("repository").putObject("pullRequest");
    ArrayNode comments = pull.putObject("comments").putArray("nodes");
    ArrayNode threads = pull.putObject("reviewThreads").putArray("nodes");
    Map<String, ArrayNode> byThread = new HashMap<>();
    for (JsonNode note : store.notes(number)) {
      String thread = note.path("thread").asString();
      if (thread.isEmpty()) {
        comment(comments.addObject(), note);
        continue;
      }
      byThread
          .computeIfAbsent(thread, id -> thread(threads, id, note))
          .addObject()
          .put("databaseId", 0);
      ArrayNode nodes = byThread.get(thread);
      comment((ObjectNode) nodes.get(nodes.size() - 1), note);
    }
    return request.toString();
  }

  private static ArrayNode thread(ArrayNode threads, String id, JsonNode first) {
    ObjectNode node = threads.addObject().put("id", id);
    node.put("isResolved", first.path("resolved").asBoolean());
    return node.putObject("comments").putArray("nodes");
  }

  private static void comment(ObjectNode into, JsonNode note) {
    into.put("databaseId", note.path("id").asInt()).put("body", note.path("body").asString());
    into.put("path", note.path("path").asString());
    into.putObject("author")
        .put("login", note.path("author").asString())
        .put("__typename", note.path("kind").asString().equals("bot") ? "Bot" : "User");
  }

  private static Map<String, String> fields(List<String> args) {
    Map<String, String> fields = new HashMap<>();
    for (int i = 0; i < args.size() - 1; i++) {
      String flag = args.get(i);
      String pair = args.get(i + 1);
      if ((flag.equals("-f") || flag.equals("-F")) && pair.contains("=")) {
        fields.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
      }
    }
    return fields;
  }

  private static String value(List<String> args, String flag) {
    int at = args.indexOf(flag);
    return at < 0 || at + 1 >= args.size() ? "" : args.get(at + 1);
  }

  private static int pullOf(String url) {
    Matcher pull = PULL.matcher(url);
    if (!pull.find()) {
      throw new StandFailure("no pull request in " + url);
    }
    return Integer.parseInt(pull.group(1));
  }

  private StandFailure approval(List<String> args) {
    store.recordApproval(String.join(" ", args));
    return new StandFailure("a stand host approves, merges and closes nothing: " + args);
  }

  private static StandFailure refuse(List<String> args) {
    return new StandFailure("the stand's gh does not answer " + String.join(" ", args));
  }
}
