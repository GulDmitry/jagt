package dev.jagt.orchestrator.port;

import java.nio.file.Path;
import java.util.List;

/**
 * One reading of a review round by a fresh model, able to read and run in the task's worktrees and to write
 * nothing there. Metered, and asked once per role per round.
 */
public interface RoundReviewer {

    /**
     * {@code shared} is what every reader of every round is given alike, apart from {@code prompt} so a later
     * reader finds it cached; blank where there is none. {@code model} blank inherits the agent CLI's own.
     */
    record Round(String shared, String prompt, List<Path> worktrees, String model) {
    }

    /** {@code severity}: blocking, wrong, unguarded or noise; only the first two stop a round. */
    record Finding(String file, String issue, String pattern, String severity) {

        public boolean stops() {
            return !severity.equals("unguarded") && !severity.equals("noise");
        }
    }

    /** {@code provenBy} is the command run and what it printed; blank where the claim was only reasoned. */
    record Premise(String claim, String provenBy) {
    }

    /** {@code failure} non-blank means nothing else in it was read. */
    record Judgement(String failure, String verdict, List<Finding> findings, String question,
                     List<Premise> premises) {

        public static Judgement failed(String failure) {
            return new Judgement(failure, "", List.of(), "", List.of());
        }
    }

    MasterAssistant.Answer<Judgement> review(Round round);
}
