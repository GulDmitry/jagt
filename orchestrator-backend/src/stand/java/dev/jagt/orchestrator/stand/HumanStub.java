package dev.jagt.orchestrator.stand;

import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class HumanStub {

    private static final String YOU = "YOU";

    private final StandMode mode;
    private final Set<String> pressed = new HashSet<>();

    public HumanStub(StandMode mode) {
        this.mode = mode;
    }

    public void take(JsonNode board, StandBoard verbs, Timeline timeline) {
        for (JsonNode task : board.path("tasks")) {
            if (task.path("owner").asString().equals(YOU)) {
                press(task, verbs, timeline);
            }
        }
    }

    private void press(JsonNode task, StandBoard verbs, Timeline timeline) {
        String id = task.path("id").asString();
        for (JsonNode action : task.path("actions")) {
            String verb = action.path("id").asString();
            String turn = id + " " + task.path("history").size() + " " + verb;
            if (action.path("primary").asBoolean() && mode.pressedByTheHuman(verb) && pressed.add(turn)) {
                timeline.note("human pressed " + verb + " on " + id + ": "
                        + verbs.press(id, verb).path("message").asString("").replaceAll("\\s+", " "));
            }
        }
    }
}
