package dev.jagt.orchestrator.adapter.herdr;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A herdr server answering the way herdr 0.9.5 does where that was observed, and refusing any
 * request {@code herdr-request-schema.json} (the real schema) does not allow.
 */
final class FakeHerdrWire implements HerdrWire {

  record Request(String method, JsonNode params) {}

  private static final Set<String> KEYS =
      Set.of(
          "esc", "up", "down", "left", "right", "tab", "space", "enter", "ctrl+c", "ctrl+u", "1",
          "2", "3", "4", "5", "6", "7", "8", "9");

  private static final class Pane {
    private final String id;
    private final String tabId;
    private String screen = "$ ";
    private boolean launched;
    private boolean agentRunning;
    private String unechoed = "";

    Pane(String id, String tabId) {
      this.id = id;
      this.tabId = tabId;
    }
  }

  private final JsonMapper json = JsonMapper.builder().build();
  private final JsonNode schema;
  private final List<Request> requests = new ArrayList<>();
  private final Map<String, String> refusals = new HashMap<>();
  private final List<String[]> workspaces = new ArrayList<>();
  private final List<String[]> tabs = new ArrayList<>();
  private final Map<String, Pane> panes = new HashMap<>();
  private boolean listening = true;
  private boolean slowShell;
  private @Nullable String downAfter;
  private @Nullable Path lastSocket;
  private int counter;

  FakeHerdrWire() throws IOException {
    try (InputStream resource = getClass().getResourceAsStream("/herdr-request-schema.json")) {
      schema = json.readTree(resource);
    }
  }

  @Override
  public Optional<String> exchange(Path socket, String request) {
    lastSocket = socket;
    if (!listening) {
      return Optional.empty();
    }
    String answer = answer(json.readTree(request));
    if (requests.getLast().method().equals(downAfter)) {
      listening = false;
    }
    return Optional.of(answer);
  }

  @Nullable Path lastSocket() {
    return lastSocket;
  }

  List<Request> requests() {
    return List.copyOf(requests);
  }

  FakeHerdrWire down() {
    listening = false;
    return this;
  }

  FakeHerdrWire up() {
    listening = true;
    return this;
  }

  FakeHerdrWire downAfter(String method) {
    downAfter = method;
    return this;
  }

  FakeHerdrWire slowShell() {
    slowShell = true;
    return this;
  }

  FakeHerdrWire shellEchoes() {
    panes.values().stream()
        .filter(p -> !p.unechoed.isEmpty())
        .forEach(p -> {
          p.screen = p.screen + p.unechoed;
          p.unechoed = "";
          p.launched = true;
          p.agentRunning = true;
        });
    return this;
  }

  FakeHerdrWire refusing(String method, String code) {
    refusals.put(method, code);
    return this;
  }

  FakeHerdrWire withTab(String label, boolean agentRunning) {
    Pane pane = createTab(workspaceId("jagt"), label);
    pane.launched = true;
    pane.agentRunning = agentRunning;
    return this;
  }

  FakeHerdrWire withTypedLineNotYetRun(String label) {
    Pane pane = createTab(workspaceId("jagt"), label);
    pane.screen = "$ exec sh -c 'agent'\n";
    return this;
  }

  FakeHerdrWire showing(String label, String screen) {
    paneOf(label).screen = screen;
    return this;
  }

  String screenOf(String label) {
    return paneOf(label).screen;
  }

  private Pane paneOf(String label) {
    String tab = tabs.stream().filter(t -> t[2].equals(label)).findFirst().orElseThrow()[0];
    return panes.values().stream().filter(p -> p.tabId.equals(tab)).findFirst().orElseThrow();
  }

  private String workspaceId(String label) {
    return workspaces.stream()
        .filter(w -> w[1].equals(label))
        .findFirst()
        .orElseGet(() -> {
          String[] created = {"w" + (workspaces.size() + 1), label};
          workspaces.add(created);
          return created;
        })[0];
  }

  private Pane createTab(String workspace, String label) {
    counter++;
    String tabId = workspace + ":t" + counter;
    tabs.add(new String[] {tabId, workspace, label});
    Pane pane = new Pane(workspace + ":p" + counter, tabId);
    panes.put(pane.id, pane);
    return pane;
  }

  private String answer(JsonNode request) {
    String method = request.path("method").asString("");
    JsonNode params = request.path("params");
    requests.add(new Request(method, params));
    String invalid = invalid(method, params);
    if (invalid != null) {
      return error("invalid_request", invalid);
    }
    if (refusals.containsKey(method)) {
      return error(refusals.get(method), method + " refused");
    }
    ObjectNode result = json.createObjectNode().put("type", "ok");
    String failure = handle(method, params, result);
    return failure == null
        ? json.createObjectNode().put("id", "jagt").set("result", result).toString()
        : error(failure, failure);
  }

  private String error(String code, String message) {
    ObjectNode body = json.createObjectNode().put("code", code).put("message", message);
    return json.createObjectNode().put("id", "jagt").set("error", body).toString();
  }

  private @Nullable String invalid(String method, JsonNode params) {
    if (!schema.path("methods").has(method)) {
      return "unknown method " + method;
    }
    JsonNode definition =
        schema.path("params").path(schema.path("methods").path(method).asString(""));
    for (JsonNode required : definition.path("required")) {
      if (!params.has(required.asString(""))) {
        return method + " lacks " + required.asString("");
      }
    }
    for (String name : params.propertyNames()) {
      if (!definition.path("properties").has(name)) {
        return method + " has no parameter " + name;
      }
      String ref = definition.path("properties").path(name).path("$ref").asString("");
      JsonNode allowed =
          schema.path("params").path(ref.substring(ref.lastIndexOf('/') + 1)).path("enum");
      if (allowed.isArray() && allowed.valueStream().noneMatch(v -> v.equals(params.path(name)))) {
        return method + " parameter " + name + " is none of " + allowed;
      }
    }
    return null;
  }

  private @Nullable String handle(String method, JsonNode params, ObjectNode result) {
    return switch (method) {
      case "ping" -> typed(result, "pong");
      case "workspace.list" -> listWorkspaces(result);
      case "workspace.create" -> createWorkspace(params, result);
      case "tab.list" -> listTabs(params, result);
      case "tab.create" -> tabCreated(params, result);
      case "tab.close" -> closeTab(params.path("tab_id").asString(""));
      case "tab.focus" -> null;
      case "pane.list" -> listPanes(result);
      case "pane.rename" -> missing(params);
      case "pane.read" -> read(params, result);
      case "pane.process_info" -> processInfo(params, result);
      case "pane.send_text" -> sent(params, params.path("text").asString(""), List.of());
      case "pane.send_keys" -> sent(params, "", keys(params));
      case "pane.send_input" -> sent(params, params.path("text").asString(""), keys(params));
      default -> "unhandled_method";
    };
  }

  private static @Nullable String typed(ObjectNode result, String type) {
    result.put("type", type);
    return null;
  }

  private static List<String> keys(JsonNode params) {
    return params.path("keys").valueStream().map(key -> key.asString("")).toList();
  }

  private @Nullable String listWorkspaces(ObjectNode result) {
    result.put("type", "workspace_list");
    var list = result.putArray("workspaces");
    workspaces.forEach(w -> list.addObject().put("workspace_id", w[0]).put("label", w[1]));
    return null;
  }

  private @Nullable String createWorkspace(JsonNode params, ObjectNode result) {
    String id = workspaceId(params.path("label").asString(""));
    result.put("type", "workspace_created").putObject("workspace").put("workspace_id", id);
    return null;
  }

  private @Nullable String listTabs(JsonNode params, ObjectNode result) {
    result.put("type", "tab_list");
    var list = result.putArray("tabs");
    tabs.stream()
        .filter(t -> t[1].equals(params.path("workspace_id").asString("")))
        .forEach(
            t -> list.addObject().put("tab_id", t[0]).put("workspace_id", t[1]).put("label", t[2]));
    return null;
  }

  private @Nullable String tabCreated(JsonNode params, ObjectNode result) {
    Pane pane =
        createTab(params.path("workspace_id").asString(""), params.path("label").asString(""));
    result.put("type", "tab_created");
    result.putObject("tab").put("tab_id", pane.tabId);
    result.putObject("root_pane").put("pane_id", pane.id).put("tab_id", pane.tabId);
    return null;
  }

  private @Nullable String closeTab(String tabId) {
    if (tabs.removeIf(t -> t[0].equals(tabId))) {
      panes.values().removeIf(p -> p.tabId.equals(tabId));
      return null;
    }
    return "tab_not_found";
  }

  private @Nullable String listPanes(ObjectNode result) {
    result.put("type", "pane_list");
    var list = result.putArray("panes");
    panes.values().forEach(p -> list.addObject().put("pane_id", p.id).put("tab_id", p.tabId));
    return null;
  }

  private Pane target(JsonNode params) {
    return Objects.requireNonNull(panes.get(params.path("pane_id").asString("")));
  }

  private @Nullable String missing(JsonNode params) {
    return panes.containsKey(params.path("pane_id").asString("")) ? null : "pane_not_found";
  }

  private @Nullable String read(JsonNode params, ObjectNode result) {
    String missing = missing(params);
    if (missing == null) {
      result.put("type", "pane_read").putObject("read").put("text", target(params).screen);
    }
    return missing;
  }

  private @Nullable String processInfo(JsonNode params, ObjectNode result) {
    String missing = missing(params);
    if (missing != null) {
      return missing;
    }
    Pane pane = target(params);
    ObjectNode info = result.put("type", "pane_process_info").putObject("process_info");
    info.put("shell_pid", 100);
    var processes = info.putArray("foreground_processes");
    if (!pane.launched) {
      processes.addObject().put("pid", 100).putArray("argv").add("-zsh");
    } else if (pane.agentRunning) {
      processes.addObject().put("pid", 200).putArray("argv").add("agent");
      processes.addObject().put("pid", 100).putArray("argv").add("sh").add("-c").add("line");
    } else {
      processes.addObject().put("pid", 100).putArray("argv").add("sleep").add("15");
    }
    return null;
  }

  private @Nullable String sent(JsonNode params, String text, List<String> keys) {
    String missing = missing(params);
    if (missing != null) {
      return missing;
    }
    if (!KEYS.containsAll(keys)) {
      return "invalid_key";
    }
    Pane pane = target(params);
    if (slowShell && text.startsWith("exec sh -c ")) {
      pane.unechoed = text;
      return null;
    }
    pane.screen =
        pane.screen + text + String.join("", keys.stream().map(k -> "<" + k + ">").toList());
    if (text.startsWith("exec sh -c ")) {
      pane.launched = true;
      pane.agentRunning = true;
    }
    return null;
  }
}
