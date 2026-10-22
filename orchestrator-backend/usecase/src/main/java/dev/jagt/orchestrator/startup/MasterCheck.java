package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.master.MasterBriefs;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.MasterRight;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The Master judges unless a human turned it off. What it judges lives in a file rather
 * than in a session, so a mode that names no readable one would run a reviewer with no standards.
 */
@Component
@RequiredArgsConstructor
public class MasterCheck implements StartupCheck {

    private static final String MODE = "orchestrator.master.mode";
    private static final String MINE = "orchestrator.master.mine";

    private final ConfigService configService;
    private final MasterBriefs briefs;
    private final AgentRuntime agentRuntime;

    @Override
    public List<Problem> problems() {
        ConfigService.ConfigFile config = configService.load();
        ConfigService.ConfigFile.MasterConfig master = config.master();
        List<Problem> problems = new ArrayList<>();
        if (MasterMode.of(master.mode()).isEmpty()) {
            problems.add(new Problem(MODE, "orchestrator.master.mode: '" + master.mode() + "' is not one of "
                    + MasterMode.ids()));
        }
        if (!master.running()) {
            return List.copyOf(problems);
        }
        if (!master.modelOrInherited().isEmpty() && !agentRuntime.choosesModel()) {
            problems.add(new Problem("orchestrator.master.model", "orchestrator.master.model: "
                    + agentRuntime.displayName() + " cannot be told which model to run — leave it blank to"
                    + " inherit its own"));
        }
        if (master.usesTheRetiredName()) {
            problems.add(new Problem("orchestrator.master.withhold", "orchestrator.master.withhold is now"
                    + " orchestrator.master.mine — the same list, named from your side rather than the Master's."
                    + " Rename the key."));
        }
        master.mineOrDefault().stream().filter(named -> MasterRight.of(named).isEmpty())
                .forEach(named -> problems.add(new Problem(MINE, "orchestrator.master.mine: '" + named
                        + "' is not one of " + MasterRight.ids())));
        if (master.modeOrDefault() == MasterMode.ACT) {
            refuseAHalfAutomatedLoop(config, master, problems);
        }
        Path brief = briefs.file(master);
        if (!Files.isRegularFile(brief)) {
            problems.add(new Problem("orchestrator.master.brief", "orchestrator.master.brief: no file at " + brief
                    + " — copy master-brief.md.dist to it and edit it until it reads like you"));
        }
        return List.copyOf(problems);
    }

    /**
     * A reviewer standing where a human stands cannot leave a step only a human can take: either it finishes a
     * round or it is judging, and a configuration saying both at once is refused rather than half-run.
     */
    private static void refuseAHalfAutomatedLoop(ConfigService.ConfigFile config,
                                                 ConfigService.ConfigFile.MasterConfig master,
                                                 List<Problem> problems) {
        if (master.may(MasterRight.REPLY) && !config.codeReview().postReviewRepliesOrDefault()) {
            problems.add(new Problem(MODE, "orchestrator.master.mode: act with codeReview.postReviewReplies: false"
                    + " leaves every round waiting for you to post the drafted replies — turn posting on, or keep"
                    + " `reply` in orchestrator.master.mine"));
        }
        if (master.may(MasterRight.DEPLOY) != master.may(MasterRight.REVERT)) {
            problems.add(new Problem(MINE, "orchestrator.master.mine: deploy and revert write the same shared"
                    + " branch, so they are one step with one owner — keep both or neither"));
        }
        if (MasterRight.ids().stream().noneMatch(right -> master.may(MasterRight.of(right).orElseThrow()))) {
            problems.add(new Problem(MINE, "orchestrator.master.mine: keeping every step in act is `judge` written"
                    + " long — set orchestrator.master.mode to " + MasterMode.JUDGE.id()));
        }
    }
}
