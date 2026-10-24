package dev.jagt.orchestrator.stand;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public class StandTools {

    private final Map<String, ObjectNode> definitions = new LinkedHashMap<>();
    private final Map<String, Function<JsonNode, JsonNode>> handlers = new LinkedHashMap<>();
    private final StandStore store;

    public StandTools(StandStore store) {
        this.store = store;
        register("get_ticket", "Read one ticket by key", "key:string", "", a -> store.ticket(text(a, "key")));
        register("list_tickets", "List every ticket", "", "", a -> store.allTickets());
        register("create_merge_request", "Open a merge request, or return the open one for the source branch",
                "source_branch:string,target_branch:string,title:string,description:string", "",
                a -> store.createRequest(text(a, "source_branch"), text(a, "target_branch"),
                        text(a, "title"), text(a, "description")));
        register("update_merge_request", "Change a merge request's title or description", "id:integer",
                "title:string,description:string", a -> store.updateRequest(number(a, "id"),
                        optional(a, "title"), optional(a, "description")));
        register("get_merge_request", "Read a merge request with its threads and pipeline by id or url",
                "id_or_url:string", "", a -> store.request(idOf(text(a, "id_or_url"))));
        register("list_merge_request_pipelines", "List the pipelines of a merge request by id", "id:integer", "",
                a -> pipelines(number(a, "id")));
        register("list_merge_requests", "List merge requests, optionally of one source branch", "",
                "source_branch:string", this::listRequests);
        register("reply_to_thread", "Reply to a review thread", "id:integer,thread_id:integer,body:string", "",
                a -> store.reply(number(a, "id"), number(a, "thread_id"), "agent", text(a, "body")));
        register("resolve_thread", "Resolve a review thread", "id:integer,thread_id:integer", "",
                a -> store.resolve(number(a, "id"), number(a, "thread_id")));
        register("get_current_user", "The account these tools act as", "", "",
                a -> JsonMapper.builder().build().createObjectNode().put("username", "agent"));
    }

    public ArrayNode definitions() {
        ArrayNode all = JsonMapper.builder().build().createArrayNode();
        definitions.values().forEach(all::add);
        return all;
    }

    public JsonNode call(String name, JsonNode arguments) {
        Function<JsonNode, JsonNode> handler = handlers.get(name);
        if (handler == null) {
            throw new StandFailure("unknown tool " + name);
        }
        return handler.apply(arguments);
    }

    private void register(String name, String description, String required, String optional,
                          Function<JsonNode, JsonNode> handler) {
        definitions.put(name, ToolSchemas.tool(name, description, required, optional));
        handlers.put(name, handler);
    }

    private JsonNode listRequests(JsonNode arguments) {
        String source = optional(arguments, "source_branch");
        ArrayNode matching = JsonMapper.builder().build().createArrayNode();
        store.allRequests().stream()
                .filter(r -> source == null || r.path("sourceBranch").asString().equals(source))
                .forEach(matching::add);
        return matching;
    }

    private JsonNode pipelines(int id) {
        JsonNode request = store.request(id);
        ArrayNode all = JsonMapper.builder().build().createArrayNode();
        all.addObject().put("id", id).put("status", request.path("pipeline").asString())
                .put("ref", request.path("sourceBranch").asString());
        return all;
    }

    private static String text(JsonNode arguments, String name) {
        JsonNode value = arguments.path(name);
        if (!value.isString()) {
            throw new StandFailure("argument " + name + " is required and must be a string");
        }
        return value.asString();
    }

    private static String optional(JsonNode arguments, String name) {
        return arguments.path(name).isString() ? arguments.path(name).asString() : null;
    }

    private static int number(JsonNode arguments, String name) {
        JsonNode value = arguments.path(name);
        if (!value.canConvertToInt()) {
            throw new StandFailure("argument " + name + " is required and must be an integer");
        }
        return value.asInt();
    }

    private static int idOf(String idOrUrl) {
        String tail = idOrUrl.substring(idOrUrl.lastIndexOf('/') + 1);
        try {
            return Integer.parseInt(tail);
        } catch (NumberFormatException e) {
            throw new StandFailure("not a merge request id or url: " + idOrUrl, e);
        }
    }
}
