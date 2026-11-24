package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public class StandStore {

  public static final String ACTOR = "stand-actor";
  public static final String OPEN = "open";

  private final ObjectMapper mapper = JsonMapper.builder().build();
  private final Path tickets;
  private final Path requests;
  private final Path pulls;
  private final Path notes;
  private final Path approvals;
  private final String baseUrl;

  public StandStore(Path directory, String baseUrl) {
    this.tickets = directory.resolve("tickets");
    this.requests = directory.resolve("requests");
    this.pulls = directory.resolve("pulls");
    this.notes = directory.resolve("notes");
    this.approvals = directory.resolve("approvals.log");
    this.baseUrl = baseUrl;
    try {
      Files.createDirectories(tickets);
      Files.createDirectories(requests);
      Files.createDirectories(pulls);
      Files.createDirectories(notes);
    } catch (IOException e) {
      throw new StandFailure("cannot create the stand directory " + directory, e);
    }
  }

  public synchronized ObjectNode seedTicket(
      String key, String title, String description, String status) {
    ObjectNode ticket = mapper.createObjectNode();
    ticket
        .put("key", key)
        .put("title", title)
        .put("description", description)
        .put("status", status);
    ticket
        .put("url", baseUrl + "/tickets/" + key)
        .put("project", key.substring(0, key.indexOf('-')));
    ticket.put("assignee", "").putArray("labels");
    write(tickets.resolve(key + ".json"), ticket);
    return ticket;
  }

  public synchronized ObjectNode ticket(String key) {
    Path file = tickets.resolve(key + ".json");
    if (!Files.exists(file)) {
      throw new StandFailure("no ticket " + key);
    }
    return read(file);
  }

  public synchronized ArrayNode allTickets() {
    return readAll(tickets);
  }

  public synchronized ObjectNode createRequest(
      String source, String target, String title, String description) {
    Optional<JsonNode> open =
        allRequests().stream()
            .filter(r ->
                r.path("state").asString().equals("open")
                    && r.path("sourceBranch").asString().equals(source))
            .findFirst();
    if (open.isPresent()) {
      return (ObjectNode) open.get();
    }
    int id = allRequests().size() + 1;
    ObjectNode request = mapper.createObjectNode();
    request.put("id", id).put("url", baseUrl + "/requests/" + id);
    request.put("sourceBranch", source).put("targetBranch", target);
    request.put("title", title).put("description", description);
    request.put("state", "open").put("pipeline", "running");
    request.putArray("threads");
    return save(request);
  }

  public synchronized ObjectNode updateRequest(int id, String title, String description) {
    ObjectNode request = request(id);
    request.put("title", title);
    request.put("description", description);
    return save(request);
  }

  public synchronized ObjectNode request(int id) {
    Path file = requests.resolve(id + ".json");
    if (!Files.exists(file)) {
      throw new StandFailure("no merge request " + id);
    }
    return read(file);
  }

  public synchronized List<JsonNode> allRequests() {
    List<JsonNode> all = new ArrayList<>();
    readAll(requests).forEach(all::add);
    all.sort(Comparator.comparingInt(request -> request.path("id").asInt()));
    return all;
  }

  public synchronized ObjectNode setPipeline(int id, String pipeline) {
    return save(request(id).put("pipeline", pipeline));
  }

  public synchronized ObjectNode addThread(int id, String file, int line, String body) {
    ObjectNode request = request(id);
    ArrayNode threads = (ArrayNode) request.path("threads");
    ObjectNode thread = threads.addObject();
    thread.put("id", threads.size()).put("file", file).put("line", line);
    thread.put("author", "reviewer").put("body", body).put("resolved", false);
    thread.putArray("replies");
    save(request);
    return thread;
  }

  public synchronized ObjectNode reply(int id, int threadId, String author, String body) {
    ObjectNode request = request(id);
    ObjectNode thread = thread(request, threadId);
    ((ArrayNode) thread.path("replies")).addObject().put("author", author).put("body", body);
    return save(request);
  }

  public synchronized ObjectNode resolve(int id, int threadId) {
    ObjectNode request = request(id);
    thread(request, threadId).put("resolved", true);
    return save(request);
  }

  /**
   * At least one thread is open, and the newest note on each open one is the agent's: the
   * reviewer's move.
   */
  public synchronized boolean awaitsTheReviewer() {
    List<JsonNode> open =
        allRequests().stream()
            .flatMap(request -> stream(request.path("threads")))
            .filter(thread -> !thread.path("resolved").asBoolean())
            .toList();
    return !open.isEmpty()
        && open.stream()
            .allMatch(thread -> {
              JsonNode replies = thread.path("replies");
              return replies.size() > 0
                  && replies.get(replies.size() - 1).path("author").asString().equals("agent");
            });
  }

  public synchronized ObjectNode seedPull(
      int number, String title, String description, String author, String head, String diff) {
    ObjectNode pull = mapper.createObjectNode();
    pull.put("number", number).put("title", title).put("description", description);
    pull.put("author", author).put("head", head).put("diff", diff);
    pull.put("state", OPEN).put("awaiting", !author.equals(ACTOR));
    write(pulls.resolve(number + ".json"), pull);
    return pull;
  }

  public synchronized ObjectNode pull(int number) {
    Path file = pulls.resolve(number + ".json");
    if (!Files.exists(file)) {
      throw new StandFailure("no pull request " + number);
    }
    return read(file);
  }

  public synchronized List<JsonNode> awaitingPulls() {
    List<JsonNode> awaiting = new ArrayList<>();
    readAll(pulls)
        .forEach(pull -> {
          if (pull.path("awaiting").asBoolean() && pull.path("state").asString().equals(OPEN)) {
            awaiting.add(pull);
          }
        });
    return awaiting;
  }

  public synchronized ObjectNode setPullState(int number, String state) {
    ObjectNode pull = pull(number).put("state", state);
    write(pulls.resolve(number + ".json"), pull);
    return pull;
  }

  public synchronized ObjectNode addNote(
      int pull, String author, String kind, String body, String path, int parent) {
    pull(pull);
    int id = allNotes().size() + 1;
    String thread = path.isBlank() ? "" : "T" + id;
    if (parent != 0) {
      thread = note(parent).path("thread").asString();
    }
    ObjectNode note = mapper.createObjectNode();
    note.put("id", id).put("pull", pull).put("author", author).put("kind", kind);
    note.put("body", body).put("path", path).put("at", StandClock.now().toString());
    note.put("thread", thread).put("resolved", false).put("likes", 0).put("dislikes", 0);
    write(notes.resolve(id + ".json"), note);
    return note;
  }

  public synchronized ObjectNode note(int id) {
    Path file = notes.resolve(id + ".json");
    if (!Files.exists(file)) {
      throw new StandFailure("no note " + id);
    }
    return read(file);
  }

  public synchronized List<JsonNode> allNotes() {
    List<JsonNode> all = new ArrayList<>();
    readAll(notes).forEach(all::add);
    all.sort(Comparator.comparingInt(note -> note.path("id").asInt()));
    return all;
  }

  public synchronized List<JsonNode> notes(int pull) {
    return allNotes().stream().filter(note -> note.path("pull").asInt() == pull).toList();
  }

  public synchronized ObjectNode react(int id, int likes, int dislikes) {
    ObjectNode note = note(id).put("likes", likes).put("dislikes", dislikes);
    write(notes.resolve(id + ".json"), note);
    return note;
  }

  public synchronized void recordApproval(String attempt) {
    try {
      Files.writeString(
          approvals,
          StandClock.now() + " " + attempt.replace('\n', ' ') + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new StandFailure("cannot record the attempt " + attempt, e);
    }
  }

  private static java.util.stream.Stream<JsonNode> stream(JsonNode threads) {
    return java.util.stream.StreamSupport.stream(threads.spliterator(), false);
  }

  private ObjectNode thread(ObjectNode request, int threadId) {
    for (JsonNode thread : request.path("threads")) {
      if (thread.path("id").asInt() == threadId) {
        return (ObjectNode) thread;
      }
    }
    throw new StandFailure(
        "no thread " + threadId + " on merge request " + request.path("id").asInt());
  }

  private ObjectNode save(ObjectNode request) {
    write(requests.resolve(request.path("id").asInt() + ".json"), request);
    return request;
  }

  private ArrayNode readAll(Path directory) {
    ArrayNode all = mapper.createArrayNode();
    try (Stream<Path> files = Files.list(directory)) {
      files.filter(f -> f.toString().endsWith(".json")).sorted().forEach(f -> all.add(read(f)));
    } catch (IOException e) {
      throw new StandFailure("cannot list " + directory, e);
    }
    return all;
  }

  private ObjectNode read(Path file) {
    return (ObjectNode) mapper.readTree(file.toFile());
  }

  private void write(Path file, ObjectNode content) {
    mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), content);
  }
}
