package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What a verdict does. Not ready goes back to the session that wrote the code, whatever the mode; ready moves
 * the task on only where a human said the reviewer stands in for them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterVerdicts {

    private final MasterReview reviews;
    private final AgentSessions sessions;
    private final CommandService commands;

    /** Answers whether the verdict moved anything, so a caller can say so without reading the file again. */
    public boolean act(String taskId, TaskState task, MasterReview.Verdict verdict,
                       ConfigService.ConfigFile.MasterConfig config) {
        if (!verdict.ready()) {
            return sessions.relayIfChanged(taskId, findings(task));
        }
        if (!config.may(MasterRight.SHIP)) {
            return false;
        }
        log.atInfo().setMessage("master ships").addKeyValue("task", taskId).log();
        // Through the same door a human's press uses, so an illegal move is refused rather than taken, and
        // stamped as the Master's so what a ship does on its behalf can differ from what it does on yours.
        OriginContext.as(ActionOrigin.MASTER, () -> commands.execute(taskId, TaskAction.SHIP));
        return true;
    }

    /** The reviewer's own words, relayed whole: shortening a finding is deciding it, which is not jagt's. */
    private String findings(TaskState task) {
        Path file = reviews.file(task);
        String said;
        try {
            said = file == null ? "" : Files.readString(file);
        } catch (IOException | RuntimeException unreadable) {
            said = "";
        }
        return "The review of your round came back NOT READY. Fix every line below, leave the fix uncommitted,"
                + " and report REVIEW_PENDING again.\n\n" + said;
    }
}
