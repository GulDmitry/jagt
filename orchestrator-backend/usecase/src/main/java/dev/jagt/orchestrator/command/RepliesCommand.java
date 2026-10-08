package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.flow.TaskView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RepliesCommand implements GlobalCommand {

    private final ReviewRepliesReport replies;

    @Override
    public String id() {
        return "replies";
    }

    @Override
    public int rank() {
        return 20;
    }

    @Override
    public String hint() {
        return "the answers a round drafted, comment by comment, before `ship` posts them";
    }

    @Override
    public List<String> usage() {
        return List.of("replies [task]");
    }

    @Override
    public boolean report() {
        return true;
    }

    @Override
    public boolean aboutOneTask() {
        return true;
    }

    @Override
    public boolean offeredOn(TaskView task) {
        return task.draftedReplies();
    }

    @Override
    public String run(String tail) {
        return replies.render(tail);
    }
}
