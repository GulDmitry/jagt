package dev.jagt.orchestrator.stand;

import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class HumanStub {

    private static final String YOU = "YOU";
    private static final Set<String> DEPLOYABLE = Set.of("REVIEWED", "APPROVED");

    private final StandMode mode;
    private final Set<String> pressed = new HashSet<>();

    public HumanStub(StandMode mode) {
        this.mode = mode;
    }

    public void take(JsonNode board, StandBoard verbs, Timeline timeline) {
        for (JsonNode task : board.path("tasks")) {
            press(task, verbs, timeline);
        }
    }

    public boolean nothingToPress(JsonNode board) {
        for (JsonNode task : board.path("tasks")) {
            for (JsonNode action : task.path("actions")) {
                if (wouldPress(task, action) && !pressed.contains(turn(task, action))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean wouldPress(JsonNode task, JsonNode action) {
        String verb = action.path("id").asString();
        return humanMove(task, action, verb) && mode.pressedByTheHuman(verb);
    }

    private static String turn(JsonNode task, JsonNode action) {
        return task.path("id").asString() + " " + task.path("history").size() + " " + action.path("id").asString();
    }

    private void press(JsonNode task, StandBoard verbs, Timeline timeline) {
        String id = task.path("id").asString();
        for (JsonNode action : task.path("actions")) {
            String verb = action.path("id").asString();
            if (wouldPress(task, action) && pressed.add(turn(task, action))) {
                timeline.note("human pressed " + verb + " on " + id + ": "
                        + verbs.press(id, verb).path("message").asString("").replaceAll("\\s+", " "));
            }
        }
    }

    private static boolean humanMove(JsonNode task, JsonNode action, String verb) {
        String status = task.path("status").asString();
        if (verb.equals("approve-plan")) {
            return status.equals("PLAN_PENDING");
        }
        if (verb.equals("deploy")) {
            return DEPLOYABLE.contains(status) && task.path("pipeline").asString().equalsIgnoreCase("green");
        }
        return task.path("owner").asString().equals(YOU) && action.path("primary").asBoolean();
    }
}
