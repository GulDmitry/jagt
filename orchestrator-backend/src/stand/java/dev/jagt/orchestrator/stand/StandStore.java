package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public class StandStore {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final Path tickets;
    private final Path requests;
    private final String baseUrl;

    public StandStore(Path directory, String baseUrl) {
        this.tickets = directory.resolve("tickets");
        this.requests = directory.resolve("requests");
        this.baseUrl = baseUrl;
        try {
            Files.createDirectories(tickets);
            Files.createDirectories(requests);
        } catch (IOException e) {
            throw new StandFailure("cannot create the stand directory " + directory, e);
        }
    }

    public synchronized ObjectNode seedTicket(String key, String title, String description, String status) {
        ObjectNode ticket = mapper.createObjectNode();
        ticket.put("key", key).put("title", title).put("description", description).put("status", status);
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

    public synchronized ObjectNode createRequest(String source, String target, String title, String description) {
        Optional<JsonNode> open = allRequests().stream()
                .filter(r -> r.path("state").asString().equals("open") && r.path("sourceBranch").asString().equals(source))
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
        if (title != null) {
            request.put("title", title);
        }
        if (description != null) {
            request.put("description", description);
        }
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

    private ObjectNode thread(ObjectNode request, int threadId) {
        for (JsonNode thread : request.path("threads")) {
            if (thread.path("id").asInt() == threadId) {
                return (ObjectNode) thread;
            }
        }
        throw new StandFailure("no thread " + threadId + " on merge request " + request.path("id").asInt());
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
