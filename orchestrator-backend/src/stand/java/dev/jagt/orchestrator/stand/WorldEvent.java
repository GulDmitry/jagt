package dev.jagt.orchestrator.stand;

import dev.jagt.orchestrator.stand.invariant.TimelineFile;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

public interface WorldEvent {

    boolean fire(StandWorld world);

    static WorldEvent reviewerThread(String body) {
        return world -> world.openRequest().map(request -> {
            int id = request.path("id").asInt();
            world.store().addThread(id, "greet.sh", 2, body);
            world.note("reviewer thread on merge request " + id + ": " + body);
            return true;
        }).orElse(false);
    }

    static WorldEvent deployBranchCommit(String file, String content, String message) {
        return world -> world.openRequest().map(request -> {
            String sha = world.project().commitOnDeployBranch(world.scratch("outsider"), file, content, message);
            world.note(TimelineFile.wroteLine(sha));
            return true;
        }).orElse(false);
    }

    static WorldEvent humanLine(String line) {
        return world -> {
            if (world.relaysSeen() < 1) {
                return false;
            }
            world.say(line);
            world.note("human typed: " + line);
            return true;
        };
    }

    static WorldEvent restart() {
        return world -> {
            if (world.relaysSeen() < 1) {
                return false;
            }
            world.note("jagt restarted while a step was pending");
            world.restart();
            return true;
        };
    }

    static WorldEvent redThenGreen() {
        return new WorldEvent() {
            private int request;
            private String head = "";

            @Override
            public boolean fire(StandWorld world) {
                return request == 0 ? red(world) : green(world);
            }

            private boolean red(StandWorld world) {
                world.openRequest().filter(open -> open.path("pipeline").asString().equals("running")).ifPresent(open -> {
                    request = open.path("id").asInt();
                    head = world.project().head(source(open));
                    world.store().setPipeline(request, "failed");
                    world.note("pipeline of merge request " + request + " set failed");
                });
                return false;
            }

            private boolean green(StandWorld world) {
                JsonNode open = world.store().request(request);
                if (Objects.equals(world.project().head(source(open)), head)) {
                    return false;
                }
                world.store().setPipeline(request, "running");
                world.note("pipeline of merge request " + request + " running again after a push");
                return true;
            }

            private String source(JsonNode open) {
                return open.path("sourceBranch").asString();
            }
        };
    }
}
