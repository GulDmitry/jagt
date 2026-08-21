package dev.jagt.orchestrator.command;

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
    public String run(String tail) {
        return replies.render(tail);
    }
}
